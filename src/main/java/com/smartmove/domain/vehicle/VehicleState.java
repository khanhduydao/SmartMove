package com.smartmove.domain.vehicle;

/**
 * Vehicle states in the lifecycle state machine.
 */
public enum VehicleState {
    AVAILABLE("Available for rental"),
    RESERVED("Reserved by user, not yet started"),
    IN_USE("Active rental in progress"),
    MAINTENANCE("Under maintenance"),
    EMERGENCY_LOCK("Emergency locked due to safety/security issue"),
    RELOCATING("Being relocated by operations team");

    private final String description;

    VehicleState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}
