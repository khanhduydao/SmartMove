package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle;

/**
 * Strategy interface for handling telemetry events.
 * Reduces cyclomatic complexity by separating event handling logic.
 */
public interface TelemetryEventHandler {
    void handle(Vehicle vehicle);
}

