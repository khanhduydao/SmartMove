package com.smartmove.telemetry;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;
import static com.smartmove.constants.SmartMoveConstants.*;
import com.smartmove.events.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background thread that continuously processes telemetry updates from vehicles.
 * Uses a BlockingQueue as the telemetry stream buffer.
 * Notifies the central controller via callback when critical conditions are detected.
 */
public class TelemetryMonitor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryMonitor.class);
    private final BlockingQueue<TelemetryUpdate> telemetryQueue = new LinkedBlockingQueue<>(TELEMETRY_QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run() {
        running.set(true);
        while (running.get() || !telemetryQueue.isEmpty()) {
            try {
                TelemetryUpdate update = telemetryQueue.poll(
                        TELEMETRY_POLL_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS
                );
                if (update == null) continue;
                processTelemetry(update);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processTelemetry(TelemetryUpdate update) {
        Vehicle v = update.vehicle();
        TelemetryData data = update.data();

        v.applyTelemetry(data);

        if (data.getTemperatureC() > CRITICAL_TEMPERATURE_C) {
            EventBus.getInstance().publish(
                    new CriticalTemperatureEvent(v, data.getTemperatureC())
            );
            return;
        }

        if (data.getTemperatureC() > WARNING_TEMPERATURE_C) {
            EventBus.getInstance().publish(
                    new HighTemperatureWarningEvent(v, data.getTemperatureC())
            );
        }

        if (data.getBatteryPercent() <= CRITICAL_BATTERY_PERCENT) {
            EventBus.getInstance().publish(
                    new CriticalBatteryEvent(v, data.getBatteryPercent())
            );
            return;
        }

        if (data.getBatteryPercent() <= LOW_BATTERY_PERCENT) {
            EventBus.getInstance().publish(
                    new LowBatteryWarningEvent(v, data.getBatteryPercent())
            );
        }

        // Theft alarm
        if (v.getState() == VehicleState.AVAILABLE || v.getState() == VehicleState.RESERVED) {
            if (update.previousLocation() != null) {
                double dist = update.previousLocation().distanceTo(data.getGps());
                if (dist > THEFT_MOVEMENT_THRESHOLD_METERS) {
                    EventBus.getInstance().publish(
                            new TheftAlarmEvent(v, dist)
                    );
                }
            }
        }
    }

    public void submitTelemetry(Vehicle v, TelemetryData data) {
        com.smartmove.domain.GeoCoordinate prevLoc = v.getLocation();
        try {
            telemetryQueue.put(new TelemetryUpdate(v, data, prevLoc));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("[TelemetryMonitor] Interrupted while submitting telemetry.");
        }
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() { return running.get(); }

    // ─── Inner record ───────────────────────────────────────────────

    private record TelemetryUpdate(
            Vehicle vehicle,
            TelemetryData data,
            com.smartmove.domain.GeoCoordinate previousLocation) {}
}
