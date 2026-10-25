package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/airspace")
public class AirspaceController {
    private final AirspaceFeatureRepository repository;
    private final ObjectMapper mapper;
    public AirspaceController(AirspaceFeatureRepository repository, ObjectMapper mapper) { this.repository = repository; this.mapper = mapper; }

    @GetMapping("/features")
    public FeatureCollection features(@RequestParam double west, @RequestParam double south,
                                      @RequestParam double east, @RequestParam double north) {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180 || east > 180 || south < -90 || north > 90 || west >= east || south >= north)
            throw new InvalidPaginationException();
        List<Object[]> rows = repository.findInBounds(west, south, east, north);
        boolean truncated = rows.size() > 2000;
        List<Feature> features = rows.stream().limit(2000).map(r -> {
            Properties properties = new Properties(String.valueOf(r[1]), String.valueOf(r[5] == null ? r[1] : r[5]),
                    String.valueOf(r[2]), String.valueOf(r[3]), String.valueOf(r[4]), String.valueOf(r[6]),
                    String.valueOf(r[7] == null ? "UNKNOWN" : r[7]), json(r[8]), String.valueOf(r[9]),
                    r[10] == null ? null : String.valueOf(r[10]), String.valueOf(r[11]));
            return new Feature("Feature", (UUID) r[0], json(r[12]), properties);
        }).toList();
        return new FeatureCollection("FeatureCollection", features, truncated);
    }

    @GetMapping("/datasets")
    public List<Dataset> datasets() {
        return repository.inventory().stream().map(r -> new Dataset(String.valueOf(r[0]), String.valueOf(r[1]),
                String.valueOf(r[2]), String.valueOf(r[3]), ((Number) r[4]).longValue(), ((Number) r[5]).longValue(),
                r[6] == null ? null : String.valueOf(r[6]), Boolean.TRUE.equals(r[7]), String.valueOf(r[8]),
                ((Number) r[9]).intValue(), ((Number) r[10]).intValue(), ((Number) r[11]).intValue(), String.valueOf(r[12]),
                r[13] == null ? null : String.valueOf(r[13]), r[14] == null ? null : String.valueOf(r[14]),
                r[15] == null ? null : String.valueOf(r[15]), "NOT_EVALUATED",
                ((Number) r[16]).intValue(), ((Number) r[17]).intValue(), ((Number) r[18]).intValue(),
                ((Number) r[19]).intValue(), ((Number) r[20]).intValue())).toList();
    }
    private JsonNode json(Object value) {
        try { return value == null ? null : mapper.readTree(String.valueOf(value)); }
        catch (Exception e) { return mapper.getNodeFactory().nullNode(); }
    }
    public record FeatureCollection(String type, List<Feature> features, boolean truncated) {}
    public record Feature(String type, UUID id, JsonNode geometry, Properties properties) {}
    public record Properties(String sourceId, String name, String source, String country, String dataset,
                             String category, String classification, JsonNode sourceProperties,
                             String geometryStatus, String effectiveAt, String importedAt) {}
    public record Dataset(String country, String source, String dataset, String category, long spatialFeatures,
                          long unconvertedRecords, String lastImportAt, boolean coverageVerified, String limitations,
                          int inputCount, int rejectedCount, int duplicateCount, String status, String sourceUrl,
                          String sourceEffectiveAt, String lastAttemptAt, String freshnessStatus,
                          int insertedCount, int updatedCount, int skippedCount, int nonSpatialCount, int failedCount) {}
}
