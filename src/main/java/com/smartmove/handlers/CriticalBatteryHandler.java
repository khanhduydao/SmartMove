package com.smartmove.handlers;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;
import com.smartmove.events.CriticalBatteryEvent;
import com.smartmove.events.EventBus; /**
 * Handles critical battery events.
 */
public class CriticalBatteryHandler implements TelemetryEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(CriticalBatteryHandler.class);
    private final VehicleStateManager stateManager;
    private final RentalTerminator rentalTerminator;
    
    public CriticalBatteryHandler(VehicleStateManager stateManager, RentalTerminator rentalTerminator) {
        this.stateManager = stateManager;
        this.rentalTerminator = rentalTerminator;
    }
    
    @Override
    public void handle(Vehicle vehicle) {
        logger.severe(() -> String.format("[CriticalBatteryHandler] Vehicle %s at %d%% - emergency action%n",
                vehicle.getId(), vehicle.getBatteryPercent()));
        
        if (vehicle.getState() == VehicleState.IN_USE) {
            rentalTerminator.terminateEmergency(vehicle, "Critical battery");
        } else {
            stateManager.sendToMaintenance(vehicle,
                    "Critical battery: %d%%".formatted(vehicle.getBatteryPercent()));
        }
        
        EventBus.getInstance().publish(
                new CriticalBatteryEvent(vehicle, vehicle.getBatteryPercent()));
    }
}
