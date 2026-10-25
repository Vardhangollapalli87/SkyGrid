package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AirspaceControllerTest {
    private final AirspaceFeatureRepository repository = mock(AirspaceFeatureRepository.class);
    private final AirspaceController controller = new AirspaceController(repository, new ObjectMapper());

    @Test void emptyViewportIsAValidEmptyFeatureCollection() {
        var response = controller.features(-10, -10, 10, 10);
        assertEquals("FeatureCollection", response.type());
        assertTrue(response.features().isEmpty());
        verify(repository).findInBounds(-10, -10, 10, 10);
    }

    @Test void rejectsInvalidViewportBounds() {
        assertThrows(InvalidPaginationException.class, () -> controller.features(10, -10, -10, 10));
        verifyNoInteractions(repository);
    }

    @Test void exposesUnverifiedDatasetStatusAndLimitations() {
        when(repository.inventory()).thenReturn(List.<Object[]>of(new Object[]{"NLD", "ED-269", "zones", "MIXED",
                162L, 612L, "2026-10-25T00:00:00Z", false, "Circles unconverted", 774, 0, 0, "SUCCESS",
                null, null, "2026-10-25T00:00:00Z", 50, 12, 0, 612, 0}));
        var row = controller.datasets().getFirst();
        assertEquals("NLD", row.country());
        assertEquals(162L, row.spatialFeatures());
        assertEquals(612L, row.unconvertedRecords());
        assertFalse(row.coverageVerified());
        assertEquals("SUCCESS", row.status());
        assertEquals("NOT_EVALUATED", row.freshnessStatus());
        assertEquals(50, row.insertedCount());
        assertEquals(12, row.updatedCount());
        assertEquals(612, row.nonSpatialCount());
    }
}
