package com.smartmove.handlers;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.domain.vehicle.Vehicle;

public class WarningEventHandler implements TelemetryEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(WarningEventHandler.class);

    @Override
    public void handle(Vehicle vehicle) {
        logger.info(() -> String.format("[WarningEventHandler] Vehicle %s: temp=%.1f°C, battery=%d%%",
                vehicle.getId(), vehicle.getTemperatureC(), vehicle.getBatteryPercent()));
    }
}
