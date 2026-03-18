package com.smartmove.domain;

import com.smartmove.config.DomainValidator;

/**
 * Geographic zone for geofencing and policy enforcement.
 */
public final class Zone {
    private final String zoneId;
    private final GeoCoordinate center;
    private final double radiusMeters;
    private final boolean restricted;

    public Zone(String zoneId, GeoCoordinate center, double radiusMeters, boolean restricted) {
        DomainValidator.requireNonNull(zoneId, "Zone ID cannot be null");
        DomainValidator.requireNonNull(center, "Zone center cannot be null");
        
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("Zone radius must be positive: " + radiusMeters);
        }
        
        this.zoneId = zoneId;
        this.center = center;
        this.radiusMeters = radiusMeters;
        this.restricted = restricted;
    }

    public String getZoneId() { return zoneId; }
    public GeoCoordinate getCenter() { return center; }
    public double getRadiusMeters() { return radiusMeters; }
    public boolean isRestricted() { return restricted; }

    /**
     * Check if a point is inside this zone.
     */
    public boolean contains(GeoCoordinate point) {
        DomainValidator.requireNonNull(point, "Point cannot be null");
        return center.distanceTo(point) <= radiusMeters;
    }

    @Override
    public String toString() {
        return String.format("Zone[%s, center=%s, radius=%.0fm, restricted=%b]",
                zoneId, center, radiusMeters, restricted);
    }
}
