package com.smartmove.config;

import com.smartmove.constants.SmartMoveConstants;

/**
 * Centralized configuration management.
 * Improves reliability through validation and default values.
 */
public class SystemConfiguration {
    
    private static final SystemConfiguration INSTANCE = new SystemConfiguration();
    
    // Telemetry settings
    private double criticalTemperatureC;
    private double warningTemperatureC;
    private int criticalBatteryPercent;
    private int lowBatteryPercent;
    private double theftMovementThresholdMeters;
    
    // Queue settings
    private int telemetryQueueCapacity;
    private long telemetryPollTimeoutMs;
    
    // File paths
    private String dataDirectory;
    
    // Pricing
    private double baseRentalAmount;
    
    private SystemConfiguration() {
        loadDefaults();
    }
    
    public static SystemConfiguration getInstance() {
        return INSTANCE;
    }
    
    /**
     * Load default configuration from constants.
     */
    private void loadDefaults() {
        this.criticalTemperatureC = SmartMoveConstants.CRITICAL_TEMPERATURE_C;
        this.warningTemperatureC = SmartMoveConstants.WARNING_TEMPERATURE_C;
        this.criticalBatteryPercent = SmartMoveConstants.CRITICAL_BATTERY_PERCENT;
        this.lowBatteryPercent = SmartMoveConstants.LOW_BATTERY_PERCENT;
        this.theftMovementThresholdMeters = SmartMoveConstants.THEFT_MOVEMENT_THRESHOLD_METERS;
        this.telemetryQueueCapacity = SmartMoveConstants.TELEMETRY_QUEUE_CAPACITY;
        this.telemetryPollTimeoutMs = SmartMoveConstants.TELEMETRY_POLL_TIMEOUT_MS;
        this.dataDirectory = SmartMoveConstants.DATA_DIR;
        this.baseRentalAmount = SmartMoveConstants.BASE_RENTAL_AMOUNT;
    }
    
    /**
     * Validate configuration values.
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (criticalTemperatureC <= 0 || criticalTemperatureC > 200) {
            throw new IllegalStateException("Invalid critical temperature: " + criticalTemperatureC);
        }
        if (warningTemperatureC >= criticalTemperatureC) {
            throw new IllegalStateException("Warning temperature must be less than critical temperature");
        }
        if (criticalBatteryPercent < 0 || criticalBatteryPercent > 100) {
            throw new IllegalStateException("Invalid critical battery percent: " + criticalBatteryPercent);
        }
        if (lowBatteryPercent <= criticalBatteryPercent) {
            throw new IllegalStateException("Low battery must be greater than critical battery");
        }
        if (telemetryQueueCapacity <= 0) {
            throw new IllegalStateException("Telemetry queue capacity must be positive");
        }
        if (baseRentalAmount < 0) {
            throw new IllegalStateException("Base rental amount cannot be negative");
        }
    }
    
    // Getters
    public double getCriticalTemperatureC() { return criticalTemperatureC; }
    public double getWarningTemperatureC() { return warningTemperatureC; }
    public int getCriticalBatteryPercent() { return criticalBatteryPercent; }
    public int getLowBatteryPercent() { return lowBatteryPercent; }
    public double getTheftMovementThresholdMeters() { return theftMovementThresholdMeters; }
    public int getTelemetryQueueCapacity() { return telemetryQueueCapacity; }
    public long getTelemetryPollTimeoutMs() { return telemetryPollTimeoutMs; }
    public String getDataDirectory() { return dataDirectory; }
    public double getBaseRentalAmount() { return baseRentalAmount; }
    
    // Setters with validation
    public void setCriticalTemperatureC(double value) {
        if (value <= 0 || value > 200) {
            throw new IllegalArgumentException("Invalid temperature: " + value);
        }
        this.criticalTemperatureC = value;
    }
    
    public void setCriticalBatteryPercent(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Invalid battery percent: " + value);
        }
        this.criticalBatteryPercent = value;
    }
    
    public void setTelemetryQueueCapacity(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.telemetryQueueCapacity = value;
    }
}

