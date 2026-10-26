package com.skygrid.mission.mission;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcAirspaceImportStoreTest {

    @Test
    void refusesToSkipMarkerWhoseFingerprintDoesNotMatchCurrentBatch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(jdbc.update(contains("insert into airspace_import_batches"), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = invocation.getArgument(1);
            java.sql.ResultSet result = mock(java.sql.ResultSet.class);
            when(result.getInt(1)).thenReturn(0);
            when(result.getInt(2)).thenReturn(0);
            when(result.getInt(3)).thenReturn(1);
            when(result.getInt(4)).thenReturn(1);
            when(result.getInt(5)).thenReturn(0);
            when(result.getInt(6)).thenReturn(0);
            when(result.getInt(7)).thenReturn(0);
            when(result.getString(8)).thenReturn("different-source-fingerprint");
            when(result.getInt(9)).thenReturn(1);
            return mapper.mapRow(result, 0);
        });
        var store = new JdbcAirspaceImportStore(jdbc, transactions);
        var dataset = new AirspaceSnapshotImporter.Dataset("USA", "FAA", "test", "RESTRICTION", 1, 0, 0, 1, "test");
        var row = new AirspaceSnapshotImporter.SourceRecord(UUID.randomUUID(), "1", "FAA", "USA", "test",
                "test", "RESTRICTION", "TEST", "{}", "{}", "PENDING_DATABASE_VALIDATION", null);

        assertThrows(IllegalStateException.class,
                () -> store.writeBatch(dataset, UUID.randomUUID(), 0, List.of(row), progress()));
        verify(jdbc, times(1)).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void retriesTransientBatchFailureOnlyUpToConfiguredBound() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("temporary database disconnect",
                        new SQLException("connection reset", "08006")));
        var store = new JdbcAirspaceImportStore(jdbc, transactions);
        var dataset = new AirspaceSnapshotImporter.Dataset("USA", "FAA", "test", "RESTRICTION", 1, 0, 0, 1, "test");
        var row = new AirspaceSnapshotImporter.SourceRecord(UUID.randomUUID(), "1", "FAA", "USA", "test",
                "test", "RESTRICTION", "TEST", "{}", "{}", "PENDING_DATABASE_VALIDATION", null);

        assertThrows(DataAccessResourceFailureException.class,
                () -> store.writeBatch(dataset, UUID.randomUUID(), 0, List.of(row), progress()));

        verify(jdbc, times(3)).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(transactions, times(3)).rollback(any());
    }

    @Test
    void permanentGeometryDataErrorDoesNotReceiveTransientRetries() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("invalid GeoJSON geometry",
                        new SQLException("invalid geometry", "22023")));
        var store = new JdbcAirspaceImportStore(jdbc, transactions);
        var dataset = new AirspaceSnapshotImporter.Dataset("USA", "FAA", "test", "RESTRICTION", 1, 0, 0, 1, "test");
        var row = new AirspaceSnapshotImporter.SourceRecord(UUID.randomUUID(), "1", "FAA", "USA", "test",
                "test", "RESTRICTION", "TEST", "{}", "{}", "PENDING_DATABASE_VALIDATION", null);

        assertThrows(AirspaceImportPersistence.InvalidSourceGeometryException.class,
                () -> store.writeBatch(dataset, UUID.randomUUID(), 0, List.of(row), progress()));

        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void refusesToFinalizeWhenCommittedBatchMarkersDoNotAccountForParsedRecords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(jdbc.queryForObject(contains("from airspace_import_batches where attempt_id=?"),
                any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = invocation.getArgument(1);
            java.sql.ResultSet result = mock(java.sql.ResultSet.class);
            when(result.getInt(1)).thenReturn(1);  // batch count
            when(result.getInt(2)).thenReturn(99); // record count, one short
            when(result.getInt(3)).thenReturn(0);
            when(result.getInt(4)).thenReturn(0);
            when(result.getInt(5)).thenReturn(99);
            when(result.getInt(6)).thenReturn(99);
            when(result.getInt(7)).thenReturn(0);
            when(result.getInt(8)).thenReturn(0);
            when(result.getInt(9)).thenReturn(0);
            return mapper.mapRow(result, 0);
        });
        var store = new JdbcAirspaceImportStore(jdbc, transactions);
        var dataset = new AirspaceSnapshotImporter.Dataset("USA", "FAA", "test", "RESTRICTION", 100,
                0, 0, 100, "test");
        var progress = new AirspaceSnapshotImporter.Progress(100, 0, 0, 100, 100, 0, 0, 0, 0);

        assertThrows(IllegalStateException.class,
                () -> store.complete(dataset, UUID.randomUUID(), progress, "SUCCESS"));
        verify(jdbc, never()).update(contains("delete from airspace_features"), any(Object[].class));
        verify(jdbc, never()).update(contains("set status=?"), any(Object[].class));
    }

    private static AirspaceSnapshotImporter.Progress progress() {
        return new AirspaceSnapshotImporter.Progress(1, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
