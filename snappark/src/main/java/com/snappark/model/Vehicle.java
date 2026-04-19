package com.snappark.model;
import com.snappark.model.enums.VehicleType;

public class Vehicle {
    private int         id;
    private String      licensePlate;
    private String      ownerName;
    private VehicleType vehicleType;
    private String      phoneNumber;   // NEW

    public Vehicle() {}

    public Vehicle(int id, String licensePlate, String ownerName,
                   VehicleType vehicleType) {
        this.id = id; this.licensePlate = licensePlate;
        this.ownerName = ownerName; this.vehicleType = vehicleType;
        this.phoneNumber = "";
    }

    public Vehicle(int id, String licensePlate, String ownerName,
                   VehicleType vehicleType, String phoneNumber) {
        this.id = id; this.licensePlate = licensePlate;
        this.ownerName = ownerName; this.vehicleType = vehicleType;
        this.phoneNumber = phoneNumber != null ? phoneNumber : "";
    }

    public int         getId()           { return id; }
    public void        setId(int id)     { this.id = id; }
    public String      getLicensePlate() { return licensePlate; }
    public void        setLicensePlate(String p) { this.licensePlate = p; }
    public String      getOwnerName()    { return ownerName; }
    public void        setOwnerName(String n)    { this.ownerName = n; }
    public VehicleType getVehicleType()  { return vehicleType; }
    public void        setVehicleType(VehicleType t) { this.vehicleType = t; }
    public String      getPhoneNumber()  { return phoneNumber != null ? phoneNumber : ""; }
    public void        setPhoneNumber(String p)  { this.phoneNumber = p; }

    @Override public String toString() {
        return "Vehicle{id="+id+", plate='"+licensePlate+"', phone='"+phoneNumber+"'}";
    }
}