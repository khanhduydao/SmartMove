package com.smartmove.policy;

import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.Rental;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.Zone;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;

import java.util.ArrayList;
import java.util.List;

public class LondonPolicy implements CityPolicy {

    private static final double CONGESTION_CHARGE = 3.50;

    // Simplified London congestion/pedestrian zones
    private static final List<Zone> CONGESTION_ZONES = new ArrayList<>();
    private static final List<Zone> MANDATORY_PARKING_ZONES = new ArrayList<>();

    static {
        // Central London congestion zone (simplified center)
        CONGESTION_ZONES.add(new Zone("LON_CONGESTION_CENTRAL",
                new GeoCoordinate(51.5155, -0.1168), 2500, true));

        // Pedestrian zone near Westminster
        CONGESTION_ZONES.add(new Zone("LON_PEDESTRIAN_WESTMINSTER",
                new GeoCoordinate(51.5010, -0.1247), 500, true));

        // Example mandatory parking bays
        MANDATORY_PARKING_ZONES.add(new Zone("LON_PARK_1",
                new GeoCoordinate(51.5074, -0.1278), 100, false));
        MANDATORY_PARKING_ZONES.add(new Zone("LON_PARK_2",
                new GeoCoordinate(51.5200, -0.0850), 100, false));
    }

    @Override
    public void beforeUnlock(Vehicle v, TelemetryData telemetryData, Rental rental)
            throws PolicyViolationException {
        if (v.getBatteryPercent() < 15) {
            throw new PolicyViolationException(
                    "London policy: battery too low to start rental (" + v.getBatteryPercent() + "%)");
        }
        System.out.println("[LondonPolicy] Pre-unlock check passed for vehicle " + v.getId());
    }

    @Override
    public double afterTrip(Rental rental, double baseAmount) throws PolicyViolationException {
        double surcharge = 0.0;
        // London applies congestion charge at end of every trip
        surcharge += CONGESTION_CHARGE;
        System.out.printf("[LondonPolicy] Applying congestion charge: £%.2f%n", CONGESTION_CHARGE);
        return surcharge;
    }

    @Override
    public boolean validateTransition(Vehicle v, VehicleState to) throws PolicyViolationException {
        // London: vehicles going into IN_USE must have at least 15% battery
        if (to == VehicleState.IN_USE && v.getBatteryPercent() < 15) {
            throw new PolicyViolationException(
                    "London policy: cannot start rental, battery at " + v.getBatteryPercent() + "% (minimum 15%)");
        }
        return true;
    }

    @Override
    public boolean isAllowed(Vehicle v, GeoCoordinate gps) throws PolicyViolationException {
        for (Zone zone : CONGESTION_ZONES) {
            if (zone.isRestricted() && zone.contains(gps)) {
                System.out.println("[LondonPolicy] Vehicle " + v.getId()
                        + " in congestion zone " + zone.getZoneId()
                        + " — congestion charge will apply.");
                // Not a hard block, but flag it (charge applied at end of trip)
            }
        }
        return true;
    }

    public boolean isInMandatoryParkingZone(GeoCoordinate gps) {
        for (Zone zone : MANDATORY_PARKING_ZONES) {
            if (zone.contains(gps)) return true;
        }
        return false;
    }
}
