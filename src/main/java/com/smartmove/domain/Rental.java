package com.smartmove.domain;

public class Rental {
    private final String id;
    private final String userId;
    private final String vehicleId;
    private final String startTime;
    private volatile String endTime;
    private volatile boolean active;

    public Rental(String id, String userId, String vehicleId, String startTime) {
        this.id = id;
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.startTime = startTime;
        this.active = true;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getVehicleId() { return vehicleId; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public boolean isActive() { return active; }

    public void end(String endTime) {
        this.endTime = endTime;
        this.active = false;
    }

    @Override
    public String toString() {
        return String.format("Rental[id=%s, user=%s, vehicle=%s, start=%s, end=%s, active=%b]",
                id, userId, vehicleId, startTime, endTime, active);
    }
}
