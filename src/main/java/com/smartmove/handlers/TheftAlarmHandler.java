package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Handles theft alarm events.
 */
public class TheftAlarmHandler implements TelemetryEventHandler {
    private final VehicleStateManager stateManager;
    
    public TheftAlarmHandler(VehicleStateManager stateManager) {
        this.stateManager = stateManager;
    }
    
    @Override
    public void handle(Vehicle vehicle) {
        System.err.printf("[TheftAlarmHandler] Vehicle %s moved without rental - emergency lock%n",
                vehicle.getId());
        
        stateManager.emergencyLock(vehicle, "Theft alarm: movement without rental");
        
        // The event will be published by caller with distance info
    }
}
