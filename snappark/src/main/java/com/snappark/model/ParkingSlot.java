package com.snappark.model;

import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;

public class ParkingSlot {
    private int id;
    private String slotNumber;
    private int floor;
    private VehicleType type;
    private SlotStatus status;

    public ParkingSlot() {}

    public ParkingSlot(int id, String slotNumber, int floor, VehicleType type, SlotStatus status) {
        this.id = id;
        this.slotNumber = slotNumber;
        this.floor = floor;
        this.type = type;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }

    @Override
    public String toString() {
        return "ParkingSlot{id=" + id + ", slotNumber='" + slotNumber + "', floor=" + floor + ", status=" + status + "}";
    }
}