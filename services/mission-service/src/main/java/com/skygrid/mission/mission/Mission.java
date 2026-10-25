package com.skygrid.mission.mission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.LineString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "geometry", columnDefinition = "geometry(Polygon,4326)")
    private Polygon geometry;

    @Column(name = "route", columnDefinition = "geometry(LineString,4326)")
    private LineString route;

    protected Mission() {
    }

    public Mission(UUID id, String name, String description, Instant createdAt) {
        this(id, name, description, createdAt, null);
    }

    public Mission(UUID id, String name, String description, Instant createdAt, Polygon geometry) {
        this(id, name, description, createdAt, geometry, null);
    }

    public Mission(UUID id, String name, String description, Instant createdAt, Polygon geometry, LineString route) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.geometry = geometry;
        this.route = route;
    }

    @PrePersist
    void assignDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Polygon getGeometry() {
        return geometry;
    }

    public LineString getRoute() { return route; }

    public void update(String name, String description, Polygon geometry, LineString route) {
        this.name = name;
        this.description = description;
        this.geometry = geometry;
        this.route = route;
    }
}
