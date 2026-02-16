package com.smartmove.domain;

public class Zone {
    private final String zoneId;
    private final GeoCoordinate center;
    private final double radiusMeters;
    private final boolean restricted;

    public Zone(String zoneId, GeoCoordinate center, double radiusMeters, boolean restricted) {
        this.zoneId = zoneId;
        this.center = center;
        this.radiusMeters = radiusMeters;
        this.restricted = restricted;
    }

    public String getZoneId() { return zoneId; }
    public GeoCoordinate getCenter() { return center; }
    public double getRadiusMeters() { return radiusMeters; }
    public boolean isRestricted() { return restricted; }

    public boolean contains(GeoCoordinate point) {
        return center.distanceTo(point) <= radiusMeters;
    }

    @Override
    public String toString() {
        return "Zone[" + zoneId + ", restricted=" + restricted + "]";
    }
}
