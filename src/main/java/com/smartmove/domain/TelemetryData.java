package com.smartmove.domain;

public class TelemetryData {
    private final String timestamp;
    private final GeoCoordinate gps;
    private final int batteryPercent;
    private final double temperatureC;
    private final boolean helmetPresent;

    public TelemetryData(String timestamp, GeoCoordinate gps,
                         int batteryPercent, double temperatureC, boolean helmetPresent) {
        this.timestamp = timestamp;
        this.gps = gps;
        this.batteryPercent = batteryPercent;
        this.temperatureC = temperatureC;
        this.helmetPresent = helmetPresent;
    }

    public String getTimestamp() { return timestamp; }
    public GeoCoordinate getGps() { return gps; }
    public int getBatteryPercent() { return batteryPercent; }
    public double getTemperatureC() { return temperatureC; }
    public boolean isHelmetPresent() { return helmetPresent; }

    public boolean isCritical() {
        return temperatureC > 60.0 || batteryPercent < 5;
    }

    @Override
    public String toString() {
        return String.format("Telemetry[time=%s, gps=%s, bat=%d%%, temp=%.1f°C, helmet=%b]",
                timestamp, gps, batteryPercent, temperatureC, helmetPresent);
    }
}
