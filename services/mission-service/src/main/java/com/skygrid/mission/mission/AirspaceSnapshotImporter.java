package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit, replay-safe snapshot importer. Source files are read locally; no network fetches occur. */
@Service
public class AirspaceSnapshotImporter {
    private static final int BATCH_SIZE = 100;
    private final ObjectMapper mapper;
    private final AirspaceImportPersistence store;

    public AirspaceSnapshotImporter(ObjectMapper mapper, AirspaceImportPersistence store) {
        this.mapper = mapper;
        this.store = store;
    }

    public List<ImportSummary> importRepositorySnapshots(Path repositoryRoot) throws Exception {
        Path snapshots = repositoryRoot.resolve("data/source-snapshots");
        List<Snapshot> sources = List.of(
                parseUsa(snapshots.resolve("faa-national-security-restrictions.geojson")),
                parseNetherlands(snapshots.resolve("netherlands-uas-geozones.json")),
                parseIndiaDocument(snapshots.resolve("india/india-enr-5-1-10-2026.html"), "ENR 5.1"),
                parseIndiaDocument(snapshots.resolve("india/india-enr-5-2-10-2026.html"), "ENR 5.2"));
        List<ImportSummary> results = new ArrayList<>();
        for (Snapshot snapshot : sources) results.add(importSnapshot(snapshot));
        return results;
    }

    ImportSummary importSnapshot(Snapshot source) {
        UUID attemptId = UUID.randomUUID();
        AtomicLong batchIds = new AtomicLong();
        Progress initial = new Progress(source.input, 0, 0, source.duplicates, 0, 0,
                source.rejected, source.duplicates, 0);
        store.begin(source.metadata(), attemptId, initial);
        Progress progress = initial;
        try {
            for (int offset = 0; offset < source.rows.size(); offset += BATCH_SIZE) {
                List<SourceRecord> chunk = source.rows.subList(offset, Math.min(offset + BATCH_SIZE, source.rows.size()));
                progress = writeWithIsolation(source, attemptId, chunk, progress, batchIds);
                System.out.printf("Airspace import progress country=%s dataset=%s parsed=%d processed=%d/%d inserted=%d updated=%d skipped=%d rejected=%d nonSpatial=%d failed=%d%n",
                        source.country, source.dataset, source.input, progress.processed(), source.input,
                        progress.inserted, progress.updated, progress.skipped, progress.rejected,
                        progress.nonSpatial, progress.failed);
            }
            if (progress.processed() != source.input) {
                throw new IllegalStateException("Airspace import did not account for every parsed source record");
            }
            String status = progress.rejected == 0 && progress.failed == 0 ? "SUCCESS" : "PARTIAL";
            store.complete(source.metadata(), attemptId, progress, status);
            return new ImportSummary(source.country, source.dataset, status, progress);
        } catch (RuntimeException failure) {
            try {
                store.fail(source.metadata(), attemptId, progress);
            } catch (RuntimeException statusFailure) {
                System.err.printf("Airspace import status could not be persisted country=%s dataset=%s exception=%s%n",
                        source.country, source.dataset, statusFailure.getClass().getSimpleName());
            }
            throw failure;
        }
    }

    private Progress writeWithIsolation(Snapshot source, UUID attemptId, List<SourceRecord> rows,
                                         Progress before, AtomicLong batchIds) {
        long batchId = batchIds.getAndIncrement();
        try {
            return store.writeBatch(source.metadata(), attemptId, batchId, rows, before);
        } catch (AirspaceImportPersistence.InvalidSourceGeometryException invalidData) {
            if (rows.size() == 1) return store.rejectRecord(source.metadata(), attemptId, batchId, rows.getFirst(), before);
            int middle = rows.size() / 2;
            Progress afterLeft = writeWithIsolation(source, attemptId, rows.subList(0, middle), before, batchIds);
            return writeWithIsolation(source, attemptId, rows.subList(middle, rows.size()), afterLeft, batchIds);
        }
    }

    private Snapshot parseUsa(Path path) throws Exception {
        JsonNode root = mapper.readTree(path.toFile());
        if (!"FeatureCollection".equals(root.path("type").asText()) || !root.path("features").isArray())
            throw new IllegalArgumentException("FAA snapshot is not a GeoJSON FeatureCollection");
        List<SourceRecord> rows = new ArrayList<>(); Set<String> ids = new HashSet<>(); int rejected = 0, duplicates = 0;
        for (JsonNode feature : root.path("features")) {
            JsonNode properties = feature.path("properties"); String id = properties.path("OBJECTID").asText("");
            if (id.isBlank()) { rejected++; continue; }
            if (!ids.add(id)) { duplicates++; continue; }
            JsonNode geometry = feature.path("geometry");
            boolean supported = geometry.isObject() && Set.of("Polygon", "MultiPolygon").contains(geometry.path("type").asText());
            rows.add(record("FAA", "USA", "National Security Restrictions", "RESTRICTION", id,
                    properties.path("Facility").asText(id), properties.path("Reason").asText("UNKNOWN"), properties,
                    supported ? geometry : null, supported ? "PENDING_DATABASE_VALIDATION" : "UNSUPPORTED_GEOMETRY", null));
        }
        return new Snapshot("USA", "FAA", "National Security Restrictions", "RESTRICTION", rows,
                root.path("features").size(), rejected, duplicates,
                "Supplied object-ID batches yielded 2,287 records; source completeness is unverified.");
    }

    private Snapshot parseNetherlands(Path path) throws Exception {
        JsonNode root = mapper.readTree(path.toFile()); JsonNode features = root.path("features");
        if (!features.isArray() || root.path("title").asText().isBlank())
            throw new IllegalArgumentException("Netherlands snapshot is not the documented ED-269 JSON collection");
        List<SourceRecord> rows = new ArrayList<>(); Set<String> ids = new HashSet<>(); int rejected = 0, duplicates = 0;
        for (JsonNode feature : features) {
            String id = feature.path("identifier").asText("");
            if (id.isBlank()) { rejected++; continue; }
            if (!ids.add(id)) { duplicates++; continue; }
            JsonNode projection = feature.path("geometry").path(0).path("horizontalProjection");
            String type = projection.path("type").asText(); boolean supported = "Polygon".equals(type);
            String classification = feature.path("restriction").asText("UNKNOWN");
            String category = categoryForNetherlandsClassification(classification);
            rows.add(record("Netherlands UAS ED-269 snapshot (provider attribution not stated in file)", "NLD",
                    root.path("title").asText("UAS geozones"), category, id, feature.path("name").asText(id),
                    classification, feature, supported ? projection : null,
                    supported ? "PENDING_DATABASE_VALIDATION" : "UNCONVERTED_" + (type.isBlank() ? "MISSING_GEOMETRY" : type), null));
        }
        String source = "Netherlands UAS ED-269 snapshot (provider attribution not stated in file)";
        String dataset = root.path("title").asText("UAS geozones");
        return new Snapshot("NLD", source, dataset, "MIXED", rows, features.size(), rejected, duplicates,
                "Only Polygon horizontal projections are spatialized; circles remain non-spatial; completeness is unverified.");
    }

    private Snapshot parseIndiaDocument(Path path, String section) throws Exception {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.toLowerCase(Locale.ROOT).contains("<html") || !text.toUpperCase(Locale.ROOT).contains(section))
            throw new IllegalArgumentException("India AIP snapshot is missing expected HTML/section marker: " + section);
        String dataset = "AAI AIP " + section + " 2026-10";
        JsonNode properties = mapper.createObjectNode().put("documentTitle", "AAI AIP " + section)
                .put("section", section).put("sourceFile", path.getFileName().toString());
        SourceRecord document = record("Airports Authority of India AIP", "IND", dataset, "AVIATION_INFORMATION",
                dataset, dataset, section + " source document; individual records not extracted", properties,
                null, "UNCONVERTED_SOURCE_DOCUMENT", text);
        return new Snapshot("IND", "Airports Authority of India AIP", dataset, "AVIATION_INFORMATION",
                List.of(document), 1, 0, 0,
                "Source HTML is retained as non-spatial text; individual boundaries and records were not extracted.");
    }

    private SourceRecord record(String source, String country, String dataset, String category, String sourceId,
                                String name, String classification, JsonNode properties, JsonNode geometry,
                                String geometryStatus, String text) {
        UUID id = UUID.nameUUIDFromBytes((source + ":" + dataset + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
        return new SourceRecord(id, sourceId, source, country, dataset, name, category, classification,
                properties.toString(), geometry == null ? null : geometry.toString(), geometryStatus, text);
    }

    static String categoryForNetherlandsClassification(String classification) {
        return Set.of("PROHIBITED", "REQ_AUTHORISATION").contains(classification)
                ? "RESTRICTION" : "AVIATION_INFORMATION";
    }

    record Snapshot(String country, String source, String dataset, String category, List<SourceRecord> rows,
                            int input, int rejected, int duplicates, String limitations) {
        Dataset metadata() { return new Dataset(country, source, dataset, category, input, rejected, duplicates, rows.size(), limitations); }
    }

    public record Dataset(String country, String source, String name, String category, int input, int rejected,
                         int duplicates, int recordCount, String limitations) {}
    public record SourceRecord(UUID id, String sourceId, String source, String country, String dataset, String name,
                               String category, String classification, String properties, String geometry,
                               String geometryStatus, String text) {}
    public record Progress(int parsed, int inserted, int updated, int skipped, int spatial, int nonSpatial,
                           int rejected, int duplicates, int failed) {
        public int processed() { return inserted + updated + skipped + rejected; }
    }
    public record ImportSummary(String country, String dataset, String status, Progress counts) {}
}
