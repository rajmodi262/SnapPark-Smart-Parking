package com.snappark.model;

import java.time.LocalDateTime;

public class ParkingSession {
    private int id;
    private int vehicleId;
    private int slotId;
    private int userId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private boolean active;
    // v2.0 fields
    private String phoneNumber;
    private String sessionPin;      // 4-digit PIN given at entry
    private String exitPin;         // 6-digit PIN given after payment
    private String paymentStatus;   // PENDING, PAID

    public ParkingSession() { this.paymentStatus = "PENDING"; }

    public ParkingSession(int id, int vehicleId, int slotId, int userId, LocalDateTime entryTime) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.slotId = slotId;
        this.userId = userId;
        this.entryTime = entryTime;
        this.active = true;
        this.paymentStatus = "PENDING";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }
    public int getSlotId() { return slotId; }
    public void setSlotId(int slotId) { this.slotId = slotId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // v2.0 getters/setters
    public String getPhoneNumber() { return phoneNumber != null ? phoneNumber : ""; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getSessionPin() { return sessionPin; }
    public void setSessionPin(String sessionPin) { this.sessionPin = sessionPin; }
    public String getExitPin() { return exitPin; }
    public void setExitPin(String exitPin) { this.exitPin = exitPin; }
    public String getPaymentStatus() { return paymentStatus != null ? paymentStatus : "PENDING"; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}