package com.skygrid.mission.mission;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists each bounded batch atomically and makes an ambiguous commit safe to retry. */
@Component
public class JdbcAirspaceImportStore implements AirspaceImportPersistence {
    private static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcAirspaceImportStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void begin(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, AirspaceSnapshotImporter.Progress initial) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                insert into airspace_dataset_imports(country_code,source_name,dataset_name,category,status,input_count,
                  spatial_count,unconverted_count,rejected_count,duplicate_count,inserted_count,updated_count,skipped_count,
                  non_spatial_count,failed_count,imported_at,last_attempt_at,attempt_id,coverage_verified,limitations)
                values (?,?,?,?,'IN_PROGRESS',?,0,0,?,?,0,0,?,0,0,null,current_timestamp,?,false,?)
                on conflict(country_code,source_name,dataset_name) do update set category=excluded.category,status='IN_PROGRESS',
                  input_count=excluded.input_count,spatial_count=0,unconverted_count=0,rejected_count=excluded.rejected_count,
                  duplicate_count=excluded.duplicate_count,inserted_count=0,updated_count=0,skipped_count=excluded.skipped_count,
                  non_spatial_count=0,failed_count=0,last_attempt_at=current_timestamp,attempt_id=excluded.attempt_id,
                  coverage_verified=false,limitations=excluded.limitations
                """, dataset.country(), dataset.source(), dataset.name(), dataset.category(), initial.parsed(),
                initial.rejected(), initial.duplicates(), initial.skipped(), attemptId, dataset.limitations()));
    }

    public AirspaceSnapshotImporter.Progress writeBatch(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                                                         long batchId, List<AirspaceSnapshotImporter.SourceRecord> rows,
                                                         AirspaceSnapshotImporter.Progress before) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> writeBatchTransaction(dataset, attemptId, batchId, rows, before));
            } catch (RuntimeException failure) {
                if (isInvalidSourceData(failure)) throw new InvalidSourceGeometryException();
                if (!isTransient(failure) || attempt >= MAX_ATTEMPTS) throw failure;
                pause(attempt);
            }
        }
    }

    private AirspaceSnapshotImporter.Progress writeBatchTransaction(AirspaceSnapshotImporter.Dataset dataset,
                                                                       UUID attemptId, long batchId,
                                                                       List<AirspaceSnapshotImporter.SourceRecord> rows,
                                                                       AirspaceSnapshotImporter.Progress before) {
        int reserved = jdbc.update("""
                insert into airspace_import_batches(attempt_id,batch_number,inserted_count,updated_count,skipped_count,
                  spatial_count,non_spatial_count,rejected_count,failed_count,source_fingerprint,record_count)
                values (?,?,0,0,0,0,0,0,0,?,?) on conflict(attempt_id,batch_number) do nothing
                """, attemptId, batchId, fingerprint(rows), rows.size());
        if (reserved == 0) {
            BatchMarker prior = jdbc.queryForObject("""
                    select inserted_count,updated_count,skipped_count,spatial_count,non_spatial_count,rejected_count,
                           failed_count,source_fingerprint,record_count
                      from airspace_import_batches where attempt_id=? and batch_number=?
                    """, (rs, rowNum) -> new BatchMarker(new int[]{rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),
                            rs.getInt(5),rs.getInt(6),rs.getInt(7)}, rs.getString(8), rs.getInt(9)),
                    attemptId, batchId);
            if (prior.recordCount != rows.size() || !fingerprint(rows).equals(prior.fingerprint)) {
                throw new IllegalStateException("Existing airspace batch marker does not match the source records");
            }
            if (sum(prior.counts, 0, 1, 2, 5) != rows.size()
                    || sum(prior.counts, 3, 4, 5) != rows.size()) {
                throw new IllegalStateException("Existing airspace batch marker has incomplete record counts");
            }
            return add(before, prior.counts);
        }

        BatchCounts counts = upsert(rows, attemptId);
        jdbc.update("""
                update airspace_import_batches set inserted_count=?,updated_count=?,skipped_count=?,spatial_count=?,
                  non_spatial_count=?,rejected_count=?,failed_count=? where attempt_id=? and batch_number=?
                """, counts.inserted, counts.updated, counts.skipped, counts.spatial, counts.nonSpatial,
                counts.rejected, counts.failed, attemptId, batchId);
        int updated = jdbc.update("""
                update airspace_dataset_imports set inserted_count=inserted_count+?,updated_count=updated_count+?,
                  skipped_count=skipped_count+?,spatial_count=spatial_count+?,unconverted_count=unconverted_count+?,
                  non_spatial_count=non_spatial_count+?,rejected_count=rejected_count+?,failed_count=failed_count+?,
                  last_attempt_at=current_timestamp
                where country_code=? and source_name=? and dataset_name=? and attempt_id=? and status='IN_PROGRESS'
                """, counts.inserted, counts.updated, counts.skipped, counts.spatial, counts.nonSpatial,
                counts.nonSpatial, counts.rejected, counts.failed, dataset.country(), dataset.source(), dataset.name(), attemptId);
        if (updated != 1) throw new IllegalStateException("Airspace import attempt is no longer current");
        return add(before, counts.asArray());
    }

    private BatchCounts upsert(List<AirspaceSnapshotImporter.SourceRecord> rows, UUID attemptId) {
        String rowPlaceholders = String.join(",", java.util.Collections.nCopies(rows.size(), "(?,?,?,?,?,?,?,?,?,cast(? as text),?,?,?)"));
        String sql = """
                with input(id,source_id,source_name,country_code,dataset_name,name,category,classification,source_properties,
                           geojson,initial_geometry_status,source_text,attempt_id) as (values %s),
                converted as materialized (
                    select i.*, case when i.geojson is null then null else ST_SetSRID(ST_GeomFromGeoJSON(i.geojson),4326) end as geom
                      from input i
                ),
                evaluated as materialized (
                    select c.*,
                      coalesce(c.geom is not null and not ST_IsEmpty(c.geom) and ST_IsValid(c.geom)
                        and ST_GeometryType(c.geom) in ('ST_Polygon','ST_MultiPolygon')
                        and ST_XMin(Box3D(c.geom)) >= -180 and ST_XMax(Box3D(c.geom)) <= 180
                        and ST_YMin(Box3D(c.geom)) >= -90 and ST_YMax(Box3D(c.geom)) <= 90,false) as valid,
                      case when c.geojson is null then c.initial_geometry_status
                           when coalesce(c.geom is not null and not ST_IsEmpty(c.geom) and ST_IsValid(c.geom)
                             and ST_GeometryType(c.geom) in ('ST_Polygon','ST_MultiPolygon')
                             and ST_XMin(Box3D(c.geom)) >= -180 and ST_XMax(Box3D(c.geom)) <= 180
                             and ST_YMin(Box3D(c.geom)) >= -90 and ST_YMax(Box3D(c.geom)) <= 90,false)
                           then 'VALID' else 'INVALID_GEOMETRY' end as final_status,
                      case when coalesce(c.geom is not null and not ST_IsEmpty(c.geom) and ST_IsValid(c.geom)
                             and ST_GeometryType(c.geom) in ('ST_Polygon','ST_MultiPolygon')
                             and ST_XMin(Box3D(c.geom)) >= -180 and ST_XMax(Box3D(c.geom)) <= 180
                             and ST_YMin(Box3D(c.geom)) >= -90 and ST_YMax(Box3D(c.geom)) <= 90,false)
                           then c.geom else null end as final_geom
                    from converted c
                ),
                prior as materialized (
                    select e.source_id,f.id is not null as existed,
                      f.id is not null
                      and f.name is not distinct from e.name
                      and f.category is not distinct from e.category
                      and f.classification is not distinct from e.classification
                      and f.geometry_status is not distinct from e.final_status
                      and f.source_properties is not distinct from cast(e.source_properties as jsonb)
                      and f.source_text is not distinct from e.source_text
                      and ST_AsEWKB(f.geometry) is not distinct from ST_AsEWKB(e.final_geom) as unchanged
                    from evaluated e left join airspace_features f
                      on f.source_name=e.source_name and f.dataset_name=e.dataset_name and f.source_id=e.source_id
                ),
                upserted as (
                    insert into airspace_features(id,source_id,source_name,country_code,dataset_name,name,category,
                      classification,geometry,source_properties,source_text,geometry_status,imported_at,last_attempt_at,last_seen_attempt_id)
                    select id,source_id,source_name,country_code,dataset_name,name,category,classification,
                      final_geom,cast(source_properties as jsonb),source_text,final_status,
                      current_timestamp,current_timestamp,attempt_id
                    from evaluated
                    on conflict(source_name,dataset_name,source_id) do update set
                      name=excluded.name,category=excluded.category,classification=excluded.classification,
                      geometry=excluded.geometry,source_properties=excluded.source_properties,source_text=excluded.source_text,
                      geometry_status=excluded.geometry_status,imported_at=excluded.imported_at,
                      last_attempt_at=excluded.last_attempt_at,last_seen_attempt_id=excluded.last_seen_attempt_id
                    returning source_id,(xmax=0) as inserted
                )
                select u.inserted,p.existed,p.unchanged,e.final_status
                  from upserted u join prior p on p.source_id=u.source_id
                  join evaluated e on e.source_id=u.source_id
                """.formatted(rowPlaceholders);
        List<Object> args = new ArrayList<>(rows.size() * 13);
        for (var row : rows) {
            args.add(row.id()); args.add(row.sourceId()); args.add(row.source()); args.add(row.country());
            args.add(row.dataset()); args.add(row.name()); args.add(row.category()); args.add(row.classification());
            args.add(row.properties()); args.add(row.geometry()); args.add(row.geometryStatus()); args.add(row.text()); args.add(attemptId);
        }
        List<UpsertResult> results = jdbc.query(sql, (rs, rowNum) -> new UpsertResult(
                rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getString(4)), args.toArray());
        int spatial = (int) results.stream().filter(result -> "VALID".equals(result.geometryStatus)).count();
        int rejectedGeometry = (int) results.stream().filter(result -> "INVALID_GEOMETRY".equals(result.geometryStatus)).count();
        int insertedCount = (int) results.stream().filter(result -> result.inserted
                && !"INVALID_GEOMETRY".equals(result.geometryStatus)).count();
        int skippedCount = (int) results.stream().filter(result -> result.existed && result.unchanged
                && !"INVALID_GEOMETRY".equals(result.geometryStatus)).count();
        int nonSpatial = (int) results.stream().filter(result -> !"VALID".equals(result.geometryStatus)
                && !"INVALID_GEOMETRY".equals(result.geometryStatus)).count();
        int updatedCount = (int) results.stream().filter(result -> !"INVALID_GEOMETRY".equals(result.geometryStatus)
                && !result.inserted && !(result.existed && result.unchanged)).count();
        return new BatchCounts(insertedCount, updatedCount, skippedCount, spatial, nonSpatial, rejectedGeometry, 0);
    }

    public AirspaceSnapshotImporter.Progress rejectRecord(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                                                           long batchId, AirspaceSnapshotImporter.SourceRecord record,
                                                           AirspaceSnapshotImporter.Progress before) {
        int[] counts = new int[]{0,0,0,0,0,1,0};
        for (int attempt = 1; ; attempt++) {
            try {
                transactions.executeWithoutResult(status -> {
                    int reserved = jdbc.update("""
                            insert into airspace_import_batches(attempt_id,batch_number,inserted_count,updated_count,skipped_count,
                              spatial_count,non_spatial_count,rejected_count,failed_count,source_fingerprint,record_count)
                            values (?,?,0,0,0,0,0,1,0,?,1) on conflict(attempt_id,batch_number) do nothing
                            """, attemptId, batchId, fingerprint(List.of(record)));
                    if (reserved == 0) {
                        BatchMarker prior = jdbc.queryForObject("""
                                select inserted_count,updated_count,skipped_count,spatial_count,non_spatial_count,rejected_count,
                                       failed_count,source_fingerprint,record_count from airspace_import_batches
                                where attempt_id=? and batch_number=?
                                """, (rs, rowNum) -> new BatchMarker(new int[]{rs.getInt(1),rs.getInt(2),rs.getInt(3),
                                rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7)},rs.getString(8),rs.getInt(9)),
                                attemptId, batchId);
                        if (prior.recordCount != 1 || !fingerprint(List.of(record)).equals(prior.fingerprint))
                            throw new IllegalStateException("Existing rejected-record marker does not match source record");
                        counts[0] = prior.counts[0]; counts[1] = prior.counts[1]; counts[2] = prior.counts[2];
                        counts[3] = prior.counts[3]; counts[4] = prior.counts[4]; counts[5] = prior.counts[5]; counts[6] = prior.counts[6];
                        return;
                    }
                    jdbc.update("""
                            insert into airspace_features(id,source_id,source_name,country_code,dataset_name,name,category,
                              classification,geometry,source_properties,source_text,geometry_status,imported_at,last_attempt_at,last_seen_attempt_id)
                            values (?,?,?,?,?,?,?,?,null,cast(? as jsonb),?,'REJECTED_INVALID_SOURCE',current_timestamp,current_timestamp,?)
                            on conflict(source_name,dataset_name,source_id) do update set
                              name=excluded.name,category=excluded.category,classification=excluded.classification,
                              geometry=null,source_properties=excluded.source_properties,source_text=excluded.source_text,
                              geometry_status=excluded.geometry_status,imported_at=excluded.imported_at,
                              last_attempt_at=excluded.last_attempt_at,last_seen_attempt_id=excluded.last_seen_attempt_id
                            """, record.id(),record.sourceId(),record.source(),record.country(),record.dataset(),record.name(),
                            record.category(),record.classification(),record.properties(),record.text(),attemptId);
                    int changed = jdbc.update("""
                            update airspace_dataset_imports set rejected_count=rejected_count+1,
                              last_attempt_at=current_timestamp where country_code=? and source_name=? and dataset_name=?
                              and attempt_id=? and status='IN_PROGRESS'
                            """, dataset.country(),dataset.source(),dataset.name(),attemptId);
                    if (changed != 1) throw new IllegalStateException("Airspace import attempt is no longer current");
                });
                return add(before, counts);
            } catch (RuntimeException failure) {
                if (!isTransient(failure) || attempt >= MAX_ATTEMPTS) throw failure;
                pause(attempt);
            }
        }
    }

    public void complete(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                         AirspaceSnapshotImporter.Progress progress, String finalStatus) {
        transactions.executeWithoutResult(status -> {
            BatchTotals totals = jdbc.queryForObject("""
                    select count(*),coalesce(sum(record_count),0),coalesce(sum(inserted_count),0),
                           coalesce(sum(updated_count),0),coalesce(sum(skipped_count),0),coalesce(sum(spatial_count),0),
                           coalesce(sum(non_spatial_count),0),coalesce(sum(rejected_count),0),coalesce(sum(failed_count),0)
                      from airspace_import_batches where attempt_id=?
                    """, (rs, rowNum) -> new BatchTotals(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                    rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9)), attemptId);
            if (totals.recordCount != dataset.recordCount()
                    || totals.inserted + totals.updated + totals.skipped + totals.rejected != dataset.recordCount()
                    || totals.spatial + totals.nonSpatial + totals.rejected != dataset.recordCount()
                    || totals.inserted != progress.inserted()
                    || totals.updated != progress.updated()
                    || totals.skipped != progress.skipped() - dataset.duplicates()
                    || totals.spatial != progress.spatial()
                    || totals.nonSpatial != progress.nonSpatial()
                    || totals.rejected != progress.rejected() - dataset.rejected()
                    || totals.failed != progress.failed()) {
                throw new IllegalStateException("Committed airspace batches do not reconcile with the parsed source");
            }
            jdbc.update("""
                    delete from airspace_features where country_code=? and source_name=? and dataset_name=?
                      and last_seen_attempt_id is distinct from ?
                    """, dataset.country(), dataset.source(), dataset.name(), attemptId);
            int changed = jdbc.update("""
                    update airspace_dataset_imports set status=?,input_count=?,spatial_count=?,unconverted_count=?,
                      rejected_count=?,duplicate_count=?,inserted_count=?,updated_count=?,skipped_count=?,non_spatial_count=?,
                      failed_count=?,imported_at=current_timestamp,last_attempt_at=current_timestamp,coverage_verified=false
                    where country_code=? and source_name=? and dataset_name=? and attempt_id=? and status='IN_PROGRESS'
                    """, finalStatus, progress.parsed(), progress.spatial(), progress.nonSpatial(), progress.rejected(),
                    progress.duplicates(), progress.inserted(), progress.updated(), progress.skipped(), progress.nonSpatial(),
                    progress.failed(), dataset.country(), dataset.source(), dataset.name(), attemptId);
            if (changed != 1) throw new IllegalStateException("Airspace import attempt is no longer current");
        });
    }

    public void fail(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, AirspaceSnapshotImporter.Progress progress) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                update airspace_dataset_imports set status='FAILED',last_attempt_at=current_timestamp,
                  failed_count=failed_count+1
                where country_code=? and source_name=? and dataset_name=? and attempt_id=? and status='IN_PROGRESS'
                """, dataset.country(), dataset.source(), dataset.name(), attemptId));
    }

    private static AirspaceSnapshotImporter.Progress add(AirspaceSnapshotImporter.Progress before, int[] counts) {
        return new AirspaceSnapshotImporter.Progress(before.parsed(), before.inserted()+counts[0], before.updated()+counts[1],
                before.skipped()+counts[2], before.spatial()+counts[3], before.nonSpatial()+counts[4],
                before.rejected()+counts[5], before.duplicates(), before.failed()+counts[6]);
    }

    private static int sum(int[] values, int... indexes) {
        int total = 0;
        for (int index : indexes) total += values[index];
        return total;
    }

    private static String fingerprint(List<AirspaceSnapshotImporter.SourceRecord> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var row : rows) {
                update(digest, row.id().toString()); update(digest, row.sourceId()); update(digest, row.source());
                update(digest, row.country()); update(digest, row.dataset()); update(digest, row.name());
                update(digest, row.category()); update(digest, row.classification()); update(digest, row.properties());
                update(digest, row.geometry()); update(digest, row.geometryStatus()); update(digest, row.text());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) { digest.update(ByteBuffer.allocate(4).putInt(-1).array()); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static boolean isInvalidSourceData(Throwable failure) {
        SQLException sql = sqlException(failure);
        return sql != null && sql.getSQLState() != null && sql.getSQLState().startsWith("22");
    }

    private static boolean isTransient(Throwable failure) {
        if (failure instanceof TransientDataAccessException) return true;
        SQLException sql = sqlException(failure);
        return sql != null && sql.getSQLState() != null
                && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("40"));
    }

    private static SQLException sqlException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql) return sql;
        return null;
    }

    private static void pause(int attempt) {
        try { Thread.sleep(Duration.ofMillis(250L * attempt).toMillis()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Import retry interrupted"); }
    }

    private record BatchCounts(int inserted, int updated, int skipped, int spatial, int nonSpatial, int rejected, int failed) {
        int[] asArray() { return new int[]{inserted,updated,skipped,spatial,nonSpatial,rejected,failed}; }
    }
    private record UpsertResult(boolean inserted, boolean existed, boolean unchanged, String geometryStatus) {}
    private record BatchMarker(int[] counts, String fingerprint, int recordCount) {}
    private record BatchTotals(int batchCount, int recordCount, int inserted, int updated, int skipped,
                               int spatial, int nonSpatial, int rejected, int failed) {}

    public static final class InvalidSourceGeometryException extends AirspaceImportPersistence.InvalidSourceGeometryException {}
}
