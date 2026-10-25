package com.skygrid.mission.mission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

@Entity
@Table(name = "restricted_zones")
public class RestrictedZone {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "geometry", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon geometry;

    @Column(name = "demonstration", nullable = false)
    private boolean demonstration;

    protected RestrictedZone() {
    }

    public RestrictedZone(UUID id, String name, Polygon geometry, boolean demonstration) {
        this.id = id;
        this.name = name;
        this.geometry = geometry;
        this.demonstration = demonstration;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Polygon getGeometry() {
        return geometry;
    }

    public boolean isDemonstration() {
        return demonstration;
    }
}
