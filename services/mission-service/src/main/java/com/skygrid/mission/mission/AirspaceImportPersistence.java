package com.skygrid.mission.mission;

import java.util.List;
import java.util.UUID;

interface AirspaceImportPersistence {
    void begin(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, AirspaceSnapshotImporter.Progress initial);
    AirspaceSnapshotImporter.Progress writeBatch(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, long batchId,
                                                  List<AirspaceSnapshotImporter.SourceRecord> rows,
                                                  AirspaceSnapshotImporter.Progress before);
    AirspaceSnapshotImporter.Progress rejectRecord(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                                                    long batchId, AirspaceSnapshotImporter.SourceRecord record,
                                                    AirspaceSnapshotImporter.Progress before);
    void complete(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId,
                  AirspaceSnapshotImporter.Progress progress, String finalStatus);
    void fail(AirspaceSnapshotImporter.Dataset dataset, UUID attemptId, AirspaceSnapshotImporter.Progress progress);

    class InvalidSourceGeometryException extends RuntimeException {}
}
