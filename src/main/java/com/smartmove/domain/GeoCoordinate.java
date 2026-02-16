package com.smartmove.domain;

public class GeoCoordinate {
    private final double latitude;
    private final double longitude;

    public GeoCoordinate(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public double distanceTo(GeoCoordinate other) {
        // Haversine formula
        double R = 6371000;
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(this.latitude))*Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    @Override
    public String toString() {
        return latitude + "," + longitude;
    }

    public static GeoCoordinate parse(String s) {
        String[] parts = s.split(",");
        return new GeoCoordinate(Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()));
    }
}
