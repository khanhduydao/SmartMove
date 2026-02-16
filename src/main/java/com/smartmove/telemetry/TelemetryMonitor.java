package com.smartmove.telemetry;

import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Background thread that continuously processes telemetry updates from vehicles.
 * Uses a BlockingQueue as the telemetry stream buffer.
 * Notifies the central controller via callback when critical conditions are detected.
 */
public class TelemetryMonitor implements Runnable {

    private static final double CRITICAL_TEMP_C   = 60.0;
    private static final double WARNING_TEMP_C    = 50.0;
    private static final int    CRITICAL_BATTERY  = 5;
    private static final int    LOW_BATTERY       = 15;

    private final BlockingQueue<TelemetryUpdate> telemetryQueue = new LinkedBlockingQueue<>(50_000);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Callback: (vehicle, event) → controller reacts
    private final BiConsumer<Vehicle, TelemetryEvent> eventCallback;

    public TelemetryMonitor(BiConsumer<Vehicle, TelemetryEvent> eventCallback) {
        this.eventCallback = eventCallback;
    }

    @Override
    public void run() {
        running.set(true);
        System.out.println("[TelemetryMonitor] Started.");
        while (running.get() || !telemetryQueue.isEmpty()) {
            try {
                TelemetryUpdate update = telemetryQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (update == null) continue;
                processTelemetry(update);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[TelemetryMonitor] Stopped.");
    }

    private void processTelemetry(TelemetryUpdate update) {
        Vehicle v = update.vehicle();
        TelemetryData data = update.data();

        // Apply telemetry to vehicle (thread-safe via vehicle's own lock)
        v.applyTelemetry(data);

        // ─── Critical temperature check ──────────────────────────────────
        if (data.getTemperatureC() > CRITICAL_TEMP_C) {
            System.err.printf("[TelemetryMonitor] CRITICAL TEMP: Vehicle %s at %.1f°C!%n",
                    v.getId(), data.getTemperatureC());
            eventCallback.accept(v, TelemetryEvent.CRITICAL_TEMPERATURE);
            return;
        }
        if (data.getTemperatureC() > WARNING_TEMP_C) {
            System.out.printf("[TelemetryMonitor] WARNING TEMP: Vehicle %s at %.1f°C%n",
                    v.getId(), data.getTemperatureC());
            eventCallback.accept(v, TelemetryEvent.HIGH_TEMPERATURE_WARNING);
        }

        // ─── Critical battery check ───────────────────────────────────────
        if (data.getBatteryPercent() <= CRITICAL_BATTERY) {
            System.err.printf("[TelemetryMonitor] CRITICAL BATTERY: Vehicle %s at %d%%%n",
                    v.getId(), data.getBatteryPercent());
            eventCallback.accept(v, TelemetryEvent.CRITICAL_BATTERY);
            return;
        }
        if (data.getBatteryPercent() <= LOW_BATTERY) {
            System.out.printf("[TelemetryMonitor] LOW BATTERY: Vehicle %s at %d%%%n",
                    v.getId(), data.getBatteryPercent());
            eventCallback.accept(v, TelemetryEvent.LOW_BATTERY_WARNING);
        }

        // ─── Theft alarm: vehicle is moving without active rental ─────────
        if (v.getState() == VehicleState.AVAILABLE || v.getState() == VehicleState.RESERVED) {
            if (update.previousLocation() != null) {
                double dist = update.previousLocation().distanceTo(data.getGps());
                if (dist > 10.0) { // moved more than 10 meters without rental
                    System.err.printf("[TelemetryMonitor] THEFT ALARM: Vehicle %s moved %.1fm without rental!%n",
                            v.getId(), dist);
                    eventCallback.accept(v, TelemetryEvent.THEFT_ALARM);
                    return;
                }
            }
        }

        // All OK
        System.out.printf("[TelemetryMonitor] Vehicle %s: bat=%d%%, temp=%.1f°C, gps=%s OK%n",
                v.getId(), data.getBatteryPercent(), data.getTemperatureC(), data.getGps());
    }

    public void submitTelemetry(Vehicle v, TelemetryData data) {
        com.smartmove.domain.GeoCoordinate prevLoc = v.getLocation();
        try {
            telemetryQueue.put(new TelemetryUpdate(v, data, prevLoc));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[TelemetryMonitor] Interrupted while submitting telemetry.");
        }
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() { return running.get(); }

    // ─── Inner record types ───────────────────────────────────────────────

    public enum TelemetryEvent {
        CRITICAL_TEMPERATURE,
        HIGH_TEMPERATURE_WARNING,
        CRITICAL_BATTERY,
        LOW_BATTERY_WARNING,
        THEFT_ALARM
    }

    private record TelemetryUpdate(
            Vehicle vehicle,
            TelemetryData data,
            com.smartmove.domain.GeoCoordinate previousLocation) {}
}
