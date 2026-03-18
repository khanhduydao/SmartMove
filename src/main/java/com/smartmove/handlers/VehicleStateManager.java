package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Interface for managing vehicle state transitions.
 * Injected into handlers for testability.
 */
public interface VehicleStateManager {
    void emergencyLock(Vehicle vehicle, String reason);
    void sendToMaintenance(Vehicle vehicle, String reason);
}
