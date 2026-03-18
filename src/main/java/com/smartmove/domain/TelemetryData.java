package com.smartmove.domain;

import com.smartmove.config.DomainValidator;
import static com.smartmove.constants.SmartMoveConstants.*;

/**
 * Telemetry data from vehicle sensors.
 * Refactored to use constants instead of magic numbers.
 */
public final class TelemetryData {
    private final String timestamp;
    private final GeoCoordinate gps;
    private final int batteryPercent;
    private final double temperatureC;
    private final boolean helmetPresent;

    public TelemetryData(String timestamp, GeoCoordinate gps,
                         int batteryPercent, double temperatureC, boolean helmetPresent) {
        DomainValidator.requireNonNull(timestamp, "Timestamp cannot be null");
        DomainValidator.requireNonNull(gps, "GPS coordinate cannot be null");
        DomainValidator.validateBatteryPercent(batteryPercent);
        DomainValidator.validateTemperature(temperatureC);
        
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

    /**
     * Check if telemetry indicates critical condition.
     * Uses constants instead of magic numbers.
     */
    public boolean isCritical() {
        return temperatureC > CRITICAL_TEMPERATURE_C 
            || batteryPercent < CRITICAL_BATTERY_PERCENT;
    }

    /**
     * Check if telemetry indicates warning condition.
     */
    public boolean isWarning() {
        return (temperatureC > WARNING_TEMPERATURE_C && temperatureC <= CRITICAL_TEMPERATURE_C)
            || (batteryPercent < LOW_BATTERY_PERCENT && batteryPercent >= CRITICAL_BATTERY_PERCENT);
    }

    @Override
    public String toString() {
        return String.format("Telemetry[time=%s, gps=%s, bat=%d%%, temp=%.1f°C, helmet=%b]",
                timestamp, gps, batteryPercent, temperatureC, helmetPresent);
    }
}
