package com.snappark.model;

/**
 * DTO for the Monthly Vehicle Visit Frequency Report.
 * Each row represents one vehicle's visit count for a given month.
 */
public class VehicleFrequencyRow {
    private final String licensePlate;
    private final String phoneNumber;
    private final String vehicleType;
    private final int    frequency;

    public VehicleFrequencyRow(String licensePlate, String phoneNumber, String vehicleType, int frequency) {
        this.licensePlate = licensePlate;
        this.phoneNumber  = phoneNumber;
        this.vehicleType  = vehicleType;
        this.frequency    = frequency;
    }

    public String getLicensePlate() { return licensePlate; }
    public String getPhoneNumber()  { return phoneNumber; }
    public String getVehicleType()  { return vehicleType; }
    public int    getFrequency()    { return frequency; }
}
