package com.smartmove.handlers;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.events.CriticalTemperatureEvent;
import com.smartmove.events.EventBus;

public class CriticalTemperatureHandler implements TelemetryEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(CriticalTemperatureHandler.class);
    private final VehicleStateManager stateManager;

    public CriticalTemperatureHandler(VehicleStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(Vehicle vehicle) {
        logger.severe(() -> String.format("[CriticalTemperatureHandler] Vehicle %s at %.1f°C - triggering emergency lock",
                vehicle.getId(), vehicle.getTemperatureC()));

        stateManager.emergencyLock(vehicle,
                "Critical temperature: " + vehicle.getTemperatureC() + "°C");

        EventBus.getInstance().publish(
                new CriticalTemperatureEvent(vehicle, vehicle.getTemperatureC()));
    }
}
