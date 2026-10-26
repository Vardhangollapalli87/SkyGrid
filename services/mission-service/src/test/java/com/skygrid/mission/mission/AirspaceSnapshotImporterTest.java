package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirspaceSnapshotImporterTest {

    @Test
    void faaRecoveryLoadsOnlyTheFaaSnapshot(@TempDir Path root) throws Exception {
        Path snapshotPath = root.resolve("data/source-snapshots/faa-national-security-restrictions.geojson");
        Files.createDirectories(snapshotPath.getParent());
        Files.writeString(snapshotPath, """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"OBJECTID":17,"Facility":"Fixture"},
                   "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}
                ]}
                """);
        var store = new MemoryStore();
        var result = new AirspaceSnapshotImporter(new ObjectMapper(), store).importFaaSnapshot(root);

        assertEquals("USA", result.country());
        assertEquals("National Security Restrictions", result.dataset());
        assertEquals("SUCCESS", result.status());
        assertEquals(1, result.counts().processed());
        assertEquals(1, store.records.size());
        assertTrue(store.records.keySet().stream().allMatch(key -> key.startsWith("FAA|National Security Restrictions|")));
    }

    @Test
    void repeatedSnapshotIsBoundedAndDoesNotCreateDuplicateSourceRecords() {
        var store = new MemoryStore();
        var importer = new AirspaceSnapshotImporter(new ObjectMapper(), store);
        var snapshot = snapshot(205);

        var first = importer.importSnapshot(snapshot);
        var second = importer.importSnapshot(snapshot);

        assertEquals("SUCCESS", first.status());
        assertEquals(205, first.counts().inserted());
        assertEquals(205, second.counts().skipped());
        assertEquals(0, second.counts().updated());
        assertEquals(0, second.counts().inserted());
        assertEquals(205, store.records.size());
        assertTrue(store.largestBatch <= 100);
        assertEquals("SUCCESS", store.status);
    }

    @Test
    void rerunningAfterAnInterruptedBatchReplaysCommittedRowsWithoutDuplicates() {
        var store = new MemoryStore();
        store.failOnSecondBatchOnce = true;
        var importer = new AirspaceSnapshotImporter(new ObjectMapper(), store);
        var snapshot = snapshot(205);

        assertThrows(IllegalStateException.class, () -> importer.importSnapshot(snapshot));
        assertEquals("FAILED", store.status);
        assertEquals(100, store.records.size());

        var retry = importer.importSnapshot(snapshot);

        assertEquals("SUCCESS", retry.status());
        assertEquals(205, store.records.size());
        assertEquals(100, retry.counts().skipped());
        assertEquals(0, retry.counts().updated());
        assertEquals(105, retry.counts().inserted());
    }

    @Test
    void permanentBadGeometryIsIsolatedAndOtherRowsCommit() {
        var store = new MemoryStore();
        var importer = new AirspaceSnapshotImporter(new ObjectMapper(), store);
        var rows = new ArrayList<>(snapshot(0).rows());
        rows.add(sourceRecord("good-1", "PENDING_DATABASE_VALIDATION"));
        rows.add(sourceRecord("bad", "INVALID_SOURCE"));
        rows.add(sourceRecord("good-2", "PENDING_DATABASE_VALIDATION"));
        var snapshot = new AirspaceSnapshotImporter.Snapshot("USA", "FAA", "test", "RESTRICTION",
                rows, rows.size(), 0, 0, "test fixture");

        var result = importer.importSnapshot(snapshot);

        assertEquals("PARTIAL", result.status());
        assertEquals(2, result.counts().inserted());
        assertEquals(1, result.counts().rejected());
        assertEquals(0, result.counts().failed());
        assertEquals(3, store.records.size());
    }

    @Test
    void invalidGeometryIsRejectedOnceAndNotAlsoCountedAsSkippedOrNonSpatial() {
        var store = new MemoryStore();
        var importer = new AirspaceSnapshotImporter(new ObjectMapper(), store);
        var invalid = sourceRecord("invalid-polygon", "INVALID_GEOMETRY");
        var snapshot = new AirspaceSnapshotImporter.Snapshot("USA", "FAA", "invalid fixture", "RESTRICTION",
                List.of(invalid), 1, 0, 0, "test fixture");

        var result = importer.importSnapshot(snapshot);
        var replay = importer.importSnapshot(snapshot);

        assertEquals("PARTIAL", result.status());
        assertEquals(1, result.counts().processed());
        assertEquals(0, result.counts().inserted());
        assertEquals(0, result.counts().skipped());
        assertEquals(0, result.counts().nonSpatial());
        assertEquals(1, result.counts().rejected());
        assertEquals(1, store.records.size());
        assertEquals("PARTIAL", replay.status());
        assertEquals(0, replay.counts().skipped());
        assertEquals(1, replay.counts().rejected());
        assertEquals(1, store.records.size());
    }

    @Test
    void onlyProhibitedOrAuthorizationRequiredNetherlandsRecordsAreRestrictions() {
        assertEquals("RESTRICTION", AirspaceSnapshotImporter.categoryForNetherlandsClassification("PROHIBITED"));
        assertEquals("RESTRICTION", AirspaceSnapshotImporter.categoryForNetherlandsClassification("REQ_AUTHORISATION"));
        assertEquals("AVIATION_INFORMATION", AirspaceSnapshotImporter.categoryForNetherlandsClassification("CONDITIONAL"));
        assertEquals("AVIATION_INFORMATION", AirspaceSnapshotImporter.categoryForNetherlandsClassification("UNKNOWN"));
    }

    private static AirspaceSnapshotImporter.Snapshot snapshot(int count) {
        List<AirspaceSnapshotImporter.SourceRecord> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(sourceRecord("id-" + i, "PENDING_DATABASE_VALIDATION"));
        return new AirspaceSnapshotImporter.Snapshot("USA", "FAA", "test", "RESTRICTION", rows, count, 0, 0, "test fixture");
    }

    private static AirspaceSnapshotImporter.SourceRecord sourceRecord(String sourceId, String geometryStatus) {
        return new AirspaceSnapshotImporter.SourceRecord(UUID.nameUUIDFromBytes(sourceId.getBytes()), sourceId,
                "FAA", "USA", "test", sourceId, "RESTRICTION", "TEST", "{}",
                "INVALID_SOURCE".equals(geometryStatus) ? "invalid" : "{\"type\":\"Polygon\"}", geometryStatus, null);
    }

    private static final class MemoryStore implements AirspaceImportPersistence {
        private final Map<String, AirspaceSnapshotImporter.SourceRecord> records = new HashMap<>();
        private String status;
        private int writeCalls;
        private int largestBatch;
        private boolean failOnSecondBatchOnce;

        @Override public void begin(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, AirspaceSnapshotImporter.Progress initial) {
            status = "IN_PROGRESS";
            writeCalls = 0;
        }

        @Override public AirspaceSnapshotImporter.Progress writeBatch(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                long batchId, List<AirspaceSnapshotImporter.SourceRecord> rows, AirspaceSnapshotImporter.Progress before) {
            writeCalls++;
            largestBatch = Math.max(largestBatch, rows.size());
            if (failOnSecondBatchOnce && writeCalls == 2) {
                failOnSecondBatchOnce = false;
                throw new IllegalStateException("simulated committed-batch interruption");
            }
            if (rows.stream().anyMatch(row -> "INVALID_SOURCE".equals(row.geometryStatus())))
                throw new InvalidSourceGeometryException();
            int inserted = 0, updated = 0, skipped = 0, rejected = 0;
            for (var row : rows) {
                String key = row.source() + "|" + row.dataset() + "|" + row.sourceId();
                var previous = records.put(key, row);
                if ("INVALID_GEOMETRY".equals(row.geometryStatus())) rejected++;
                else if (previous == null) inserted++;
                else if (previous.equals(row)) skipped++;
                else updated++;
            }
            int spatial = (int) rows.stream().filter(row -> "PENDING_DATABASE_VALIDATION".equals(row.geometryStatus())).count();
            int nonSpatial = (int) rows.stream().filter(row -> row.geometry() == null
                    && !"INVALID_GEOMETRY".equals(row.geometryStatus())).count();
            return new AirspaceSnapshotImporter.Progress(before.parsed(), before.inserted()+inserted,
                    before.updated()+updated, before.skipped()+skipped, before.spatial()+spatial,
                    before.nonSpatial()+nonSpatial, before.rejected()+rejected, before.duplicates(), before.failed());
        }

        @Override public AirspaceSnapshotImporter.Progress rejectRecord(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                long batchId, AirspaceSnapshotImporter.SourceRecord record, AirspaceSnapshotImporter.Progress before) {
            records.put(record.source() + "|" + record.dataset() + "|" + record.sourceId(), record);
            return new AirspaceSnapshotImporter.Progress(before.parsed(), before.inserted(), before.updated(), before.skipped(),
                    before.spatial(), before.nonSpatial(), before.rejected()+1, before.duplicates(), before.failed());
        }

        @Override public void complete(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                AirspaceSnapshotImporter.Progress progress, String finalStatus) { status = finalStatus; }

        @Override public void fail(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                AirspaceSnapshotImporter.Progress progress) { status = "FAILED"; }
    }
}
