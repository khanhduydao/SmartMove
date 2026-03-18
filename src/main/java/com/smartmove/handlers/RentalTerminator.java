package com.smartmove.handlers;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Interface for terminating rentals.
 * Injected into handlers for testability.
 */
public interface RentalTerminator {
    void terminateEmergency(Vehicle vehicle, String reason);
}
