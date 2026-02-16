package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData;

import java.util.HashMap;
import java.util.Map;

public abstract class Vehicle {
    protected final String id;
    protected volatile VehicleState state;
    protected volatile int batteryPercent;
    protected volatile double temperatureC;
    protected volatile GeoCoordinate location;
    protected final City city;
    protected final Map<String, Object> vehicleLocks = new HashMap<>();

    // Lock object for thread-safe state transitions
    private final Object stateLock = new Object();

    public Vehicle(String id, City city, GeoCoordinate location, int batteryPercent) {
        this.id = id;
        this.state = VehicleState.AVAILABLE;
        this.city = city;
        this.location = location;
        this.batteryPercent = batteryPercent;
        this.temperatureC = 20.0;
    }

    public abstract String getType();

    // Thread-safe state transition
    public boolean transitionTo(VehicleState newState) {
        synchronized (stateLock) {
            if (isValidTransition(this.state, newState)) {
                this.state = newState;
                return true;
            }
            return false;
        }
    }

    public boolean isValidTransition(VehicleState from, VehicleState to) {
        switch (from) {
            case AVAILABLE:
                return to == VehicleState.RESERVED || to == VehicleState.MAINTENANCE
                        || to == VehicleState.EMERGENCY_LOCK || to == VehicleState.RELOCATING;
            case RESERVED:
                return to == VehicleState.IN_USE || to == VehicleState.AVAILABLE
                        || to == VehicleState.EMERGENCY_LOCK;
            case IN_USE:
                return to == VehicleState.AVAILABLE || to == VehicleState.MAINTENANCE
                        || to == VehicleState.EMERGENCY_LOCK;
            case MAINTENANCE:
                return to == VehicleState.AVAILABLE || to == VehicleState.EMERGENCY_LOCK;
            case EMERGENCY_LOCK:
                return to == VehicleState.MAINTENANCE || to == VehicleState.AVAILABLE;
            case RELOCATING:
                return to == VehicleState.AVAILABLE || to == VehicleState.MAINTENANCE;
            default:
                return false;
        }
    }

    public void applyTelemetry(TelemetryData t) {
        synchronized (stateLock) {
            this.location = t.getGps();
            this.batteryPercent = t.getBatteryPercent();
            this.temperatureC = t.getTemperatureC();
        }
    }

    // Getters
    public String getId() { return id; }
    public VehicleState getState() { return state; }
    public int getBatteryPercent() { return batteryPercent; }
    public double getTemperatureC() { return temperatureC; }
    public GeoCoordinate getLocation() { return location; }
    public City getCity() { return city; }
    public Map<String, Object> getVehicleLocks() { return vehicleLocks; }
    public Object getStateLock() { return stateLock; }

    @Override
    public String toString() {
        return String.format("%s[id=%s, state=%s, bat=%d%%, temp=%.1f°C, city=%s]",
                getType(), id, state, batteryPercent, temperatureC, city.getName());
    }
}
