package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.events.CriticalTemperatureEvent;
import com.smartmove.events.EventBus; /**
 * Handles critical temperature events.
 */
public class CriticalTemperatureHandler implements TelemetryEventHandler {
    private final VehicleStateManager stateManager;
    
    public CriticalTemperatureHandler(VehicleStateManager stateManager) {
        this.stateManager = stateManager;
    }
    
    @Override
    public void handle(Vehicle vehicle) {
        System.err.printf("[CriticalTemperatureHandler] Vehicle %s at %.1f°C - triggering emergency lock%n",
                vehicle.getId(), vehicle.getTemperatureC());
        
        stateManager.emergencyLock(vehicle, 
                "Critical temperature: " + vehicle.getTemperatureC() + "°C");
        
        // Publish event
        EventBus.getInstance().publish(
                new CriticalTemperatureEvent(vehicle, vehicle.getTemperatureC()));
    }
}
