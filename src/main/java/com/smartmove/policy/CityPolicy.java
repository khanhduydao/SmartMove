package com.smartmove.policy;

import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.Rental;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.vehicle.Vehicle;

public interface CityPolicy {
    /**
     * Called before unlocking a vehicle. Throws PolicyViolationException if not allowed.
     */
    void beforeUnlock(Vehicle v, TelemetryData telemetryData, Rental rental) throws PolicyViolationException;

    /**
     * Called after a trip ends. Returns additional surcharge amount to add to base fare.
     */
    double afterTrip(Rental rental, double baseAmount) throws PolicyViolationException;

    /**
     * Validates a state transition considering city-specific rules.
     */
    boolean validateTransition(Vehicle v, com.smartmove.domain.vehicle.VehicleState to) throws PolicyViolationException;

    /**
     * Checks if a vehicle is allowed at given GPS coordinates.
     */
    boolean isAllowed(Vehicle v, GeoCoordinate gps) throws PolicyViolationException;
}
