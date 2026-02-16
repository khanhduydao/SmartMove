package com.smartmove.policy;

import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.Rental;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.Zone;
import com.smartmove.domain.vehicle.ElectricScooter;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;

import java.util.ArrayList;
import java.util.List;

public class RomePolicy implements CityPolicy {

    // Zones restricted to electric scooters (archaeological/pedestrian areas)
    private static final List<Zone> SCOOTER_RESTRICTED_ZONES = new ArrayList<>();
    // General restricted zones for all vehicles
    private static final List<Zone> GENERAL_RESTRICTED_ZONES = new ArrayList<>();

    static {
        // Colosseum & Roman Forum archaeological area
        SCOOTER_RESTRICTED_ZONES.add(new Zone("ROME_ARCHAEOLOGICAL_COLOSSEO",
                new GeoCoordinate(41.8902, 12.4922), 800, true));
        // Vatican area
        SCOOTER_RESTRICTED_ZONES.add(new Zone("ROME_VATICAN",
                new GeoCoordinate(41.9029, 12.4534), 600, true));
        // Piazza Navona pedestrian area
        SCOOTER_RESTRICTED_ZONES.add(new Zone("ROME_PIAZZA_NAVONA",
                new GeoCoordinate(41.8992, 12.4731), 200, true));

        // ZTL (Zona Traffico Limitato) restricted for all vehicles
        GENERAL_RESTRICTED_ZONES.add(new Zone("ROME_ZTL_CENTRO",
                new GeoCoordinate(41.8956, 12.4820), 1500, true));
    }

    @Override
    public void beforeUnlock(Vehicle v, TelemetryData telemetryData, Rental rental)
            throws PolicyViolationException {
        if (v.getBatteryPercent() < 15) {
            throw new PolicyViolationException(
                    "Rome policy: battery too low (" + v.getBatteryPercent() + "%)");
        }
        // Check current location isn't already in a restricted zone
        if (telemetryData != null) {
            isAllowed(v, telemetryData.getGps());
        }
        System.out.println("[RomePolicy] Pre-unlock check passed for vehicle " + v.getId());
    }

    @Override
    public double afterTrip(Rental rental, double baseAmount) throws PolicyViolationException {
        System.out.printf("[RomePolicy] Trip completed. Base cost: %.2f%n", baseAmount);
        return 0.0; // No additional surcharge in Rome by default
    }

    @Override
    public boolean validateTransition(Vehicle v, VehicleState to) throws PolicyViolationException {
        return true; // Rome has no additional transition constraints
    }

    @Override
    public boolean isAllowed(Vehicle v, GeoCoordinate gps) throws PolicyViolationException {
        // Check general restrictions for all vehicles
        for (Zone zone : GENERAL_RESTRICTED_ZONES) {
            if (zone.isRestricted() && zone.contains(gps)) {
                throw new PolicyViolationException(
                        "Rome policy: Vehicle " + v.getId()
                                + " is entering restricted ZTL zone " + zone.getZoneId());
            }
        }
        // Check scooter-specific archaeological/pedestrian restrictions
        if (v instanceof ElectricScooter) {
            for (Zone zone : SCOOTER_RESTRICTED_ZONES) {
                if (zone.isRestricted() && zone.contains(gps)) {
                    throw new PolicyViolationException(
                            "Rome policy: Scooter " + v.getId()
                                    + " not allowed in protected zone " + zone.getZoneId()
                                    + " (archaeological/pedestrian area)");
                }
            }
        }
        return true;
    }
}
