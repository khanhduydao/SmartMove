package com.smartmove.policy;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import static com.smartmove.constants.SmartMoveConstants.*;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.Rental;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.Zone;
import com.smartmove.domain.vehicle.Moped;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;

import java.util.ArrayList;
import java.util.List;

public class MilanPolicy implements CityPolicy {
    private static final Logger logger = LoggerFactory.getLogger(MilanPolicy.class);
    private static final List<Zone> RESTRICTED_ZONES = new ArrayList<>();
    private static final Zone CITY_CENTER_ZONE;

    static {
        // ZTL (Zona a Traffico Limitato) areas in Milan
        RESTRICTED_ZONES.add(new Zone("MIL_ZTL_CENTRO",
                new GeoCoordinate(45.4642, 9.1900), 1200, true));
        RESTRICTED_ZONES.add(new Zone("MIL_PROTECTED_PARCO",
                new GeoCoordinate(45.4773, 9.1878), 600, true));

        // City center zone for higher pricing
        CITY_CENTER_ZONE = new Zone("MIL_CITY_CENTER",
                new GeoCoordinate(45.4654, 9.1866), 2000, false);
    }

    @Override
    public void beforeUnlock(Vehicle v, TelemetryData telemetryData, Rental rental)
            throws PolicyViolationException {
        // Milan: Mopeds require helmet sensor confirmation before unlocking
        if (v instanceof Moped) {
            if (telemetryData == null || !telemetryData.isHelmetPresent()) {
                throw new PolicyViolationException(
                        "Milan policy: Helmet not detected! Moped " + v.getId()
                                + " cannot be unlocked without confirmed helmet presence.");
            }
            logger.info("[MilanPolicy] Helmet confirmed for Moped " + v.getId());
        }
        if (v.getBatteryPercent() < MILAN_MIN_BATTERY_PERCENT) {
            throw new PolicyViolationException(
                    "Milan policy: battery too low (" + v.getBatteryPercent() + "%)");
        }
    }

    @Override
    public double afterTrip(Rental rental, double baseAmount) throws PolicyViolationException {
        double surcharge = 0.0;
        // No city-specific surcharge by default, but city center adds extra
        logger.info(String.format("[MilanPolicy] Base trip cost: %.2f%n", baseAmount));
        return surcharge;
    }

    @Override
    public boolean validateTransition(Vehicle v, VehicleState to) throws PolicyViolationException {
        if (to == VehicleState.IN_USE && v instanceof Moped) {
            Moped moped = (Moped) v;
            if (!moped.isHelmetDetected()) {
                throw new PolicyViolationException(
                        "Milan policy: Moped requires helmet sensor confirmation before use.");
            }
        }
        return true;
    }

    @Override
    public boolean isAllowed(Vehicle v, GeoCoordinate gps) throws PolicyViolationException {
        for (Zone zone : RESTRICTED_ZONES) {
            if (zone.isRestricted() && zone.contains(gps)) {
                throw new PolicyViolationException(
                        "Milan policy: Vehicle " + v.getId()
                                + " entered restricted zone " + zone.getZoneId()
                                + ". Emergency lock triggered!");
            }
        }
        return true;
    }

    public boolean isInCityCenter(GeoCoordinate gps) {
        return CITY_CENTER_ZONE.contains(gps);
    }

    public double getCityCenterSurcharge() { return MILAN_CITY_CENTER_SURCHARGE; }
}
