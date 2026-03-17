package com.smartmove.domain;

import com.smartmove.config.DomainValidator;

/**
 * Immutable geographic coordinate with validated ranges.
 * Refactored to include defensive validation.
 */
public final class GeoCoordinate {
    private final double latitude;
    private final double longitude;

    public GeoCoordinate(double latitude, double longitude) {
        DomainValidator.validateCoordinate(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    /**
     * Calculate distance to another coordinate using Haversine formula.
     * @param other target coordinate
     * @return distance in meters
     */
    public double distanceTo(GeoCoordinate other) {
        DomainValidator.requireNonNull(other, "Target coordinate cannot be null");
        
        final double EARTH_RADIUS_METERS = 6_371_000;
        
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(this.latitude))
                * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_METERS * c;
    }

    @Override
    public String toString() {
        return String.format("%.6f,%.6f", latitude, longitude);
    }

    public static GeoCoordinate parse(String s) {
        DomainValidator.requireNonNull(s, "Coordinate string cannot be null");
        
        String[] parts = s.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid coordinate format: " + s);
        }
        
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            return new GeoCoordinate(lat, lon);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate values: " + s, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GeoCoordinate)) return false;
        GeoCoordinate other = (GeoCoordinate) obj;
        return Double.compare(latitude, other.latitude) == 0
            && Double.compare(longitude, other.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(latitude, longitude);
    }
}
