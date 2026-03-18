package com.smartmove.handlers;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.domain.vehicle.Vehicle;

public class TheftAlarmHandler implements TelemetryEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(TheftAlarmHandler.class);
    private final VehicleStateManager stateManager;

    public TheftAlarmHandler(VehicleStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void handle(Vehicle vehicle) {
        logger.severe(() -> "[TheftAlarmHandler] Vehicle " + vehicle.getId()
                + " moved without rental - emergency lock");

        stateManager.emergencyLock(vehicle, "Theft alarm: movement without rental");
    }
}
