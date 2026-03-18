package com.smartmove.builder;

import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData; /**
 * Builder for TelemetryData.
 */
public class TelemetryDataBuilder {
    private String timestamp = java.time.Instant.now().toString();
    private GeoCoordinate gps;
    private int batteryPercent = 80;
    private double temperatureC = 20.0;
    private boolean helmetPresent = false;
    
    public static TelemetryDataBuilder aTelemetryData() {
        return new TelemetryDataBuilder();
    }
    
    public TelemetryDataBuilder at(double lat, double lon) {
        this.gps = new GeoCoordinate(lat, lon);
        return this;
    }
    
    public TelemetryDataBuilder at(GeoCoordinate gps) {
        this.gps = gps;
        return this;
    }
    
    public TelemetryDataBuilder withBattery(int percent) {
        this.batteryPercent = percent;
        return this;
    }
    
    public TelemetryDataBuilder withTemperature(double celsius) {
        this.temperatureC = celsius;
        return this;
    }
    
    public TelemetryDataBuilder withHelmet(boolean present) {
        this.helmetPresent = present;
        return this;
    }
    
    public TelemetryDataBuilder critical() {
        this.temperatureC = 75.0;
        this.batteryPercent = 3;
        return this;
    }
    
    public TelemetryDataBuilder normal() {
        this.temperatureC = 20.0;
        this.batteryPercent = 80;
        return this;
    }
    
    public TelemetryData build() {
        if (gps == null) {
            throw new IllegalStateException("GPS location is required");
        }
        return new TelemetryData(timestamp, gps, batteryPercent, temperatureC, helmetPresent);
    }
}
