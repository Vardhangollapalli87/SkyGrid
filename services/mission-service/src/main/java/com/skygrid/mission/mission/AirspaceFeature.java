package com.skygrid.mission.mission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Source-preserving airspace record; geometry may be null when source geometry cannot be represented safely. */
@Entity
@Table(name = "airspace_features", uniqueConstraints = @UniqueConstraint(
        name = "uq_airspace_source_identity", columnNames = {"source_name", "dataset_name", "source_id"}))
public class AirspaceFeature {
    @Id
    private UUID id;
    @Column(name = "source_id", nullable = false)
    private String sourceId;
    @Column(name = "source_name", nullable = false)
    private String sourceName;
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;
    @Column(name = "dataset_name", nullable = false)
    private String datasetName;
    @Column(name = "name")
    private String name;
    @Column(nullable = false, length = 32)
    private String category;
    @Column
    private String classification;
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_properties", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sourceProperties;
    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;
    @Column(name = "geometry_status", nullable = false, length = 40)
    private String geometryStatus;
    @Column(name = "source_effective_at")
    private Instant sourceEffectiveAt;
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;
    @Column(name = "last_seen_attempt_id")
    private UUID lastSeenAttemptId;

    protected AirspaceFeature() {}

    public UUID getId() { return id; }
    public String getSourceId() { return sourceId; }
    public String getSourceName() { return sourceName; }
    public String getCountryCode() { return countryCode; }
    public String getDatasetName() { return datasetName; }
    public String getCategory() { return category; }
    public String getClassification() { return classification; }
    public Geometry getGeometry() { return geometry; }
    public Map<String, Object> getSourceProperties() { return sourceProperties; }
    public String getSourceText() { return sourceText; }
    public String getGeometryStatus() { return geometryStatus; }
    public Instant getSourceEffectiveAt() { return sourceEffectiveAt; }
    public Instant getImportedAt() { return importedAt; }
}
