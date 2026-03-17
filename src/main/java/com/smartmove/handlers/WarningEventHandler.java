package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Handles warning level events.
 */
public class WarningEventHandler implements TelemetryEventHandler {
    @Override
    public void handle(Vehicle vehicle) {
        // Log warning but don't take drastic action
        System.out.printf("[WarningEventHandler] Vehicle %s: temp=%.1f°C, battery=%d%%%n",
                vehicle.getId(), vehicle.getTemperatureC(), vehicle.getBatteryPercent());
    }
}
