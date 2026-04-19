package com.snappark.service;

import java.time.Duration;
import java.time.LocalDateTime;
import com.snappark.model.enums.VehicleType;

public class BillingService {
    private static BillingService instance;
    private final SurgePricingService surgeService = SurgePricingService.getInstance();

    private BillingService() {}

    public static BillingService getInstance() {
        if (instance == null) instance = new BillingService();
        return instance;
    }

    /**
     * Calculate parking fee with surge pricing.
     */
    public double calculateFee(VehicleType type, LocalDateTime entry, LocalDateTime exit) {
        Duration duration = Duration.between(entry, exit);
        long minutes = duration.toMinutes();
        if (minutes < 1) minutes = 1;
        // Grace period: first 15 minutes free
        if (minutes <= 15) return 0.0;
        double hours = Math.ceil((minutes - 15) / 30.0) / 2.0;
        if (hours < 1.0) hours = 1.0;

        // Get surge-adjusted rate
        SurgePricingService.PriceBreakdown breakdown = surgeService.getBreakdown(
            type,
            ParkingService.getInstance().getOccupiedCount(),
            ParkingService.getInstance().getAllSlots().size(),
            entry.toLocalTime()
        );

        return Math.round(hours * breakdown.effectiveRate * 100.0) / 100.0;
    }

    /**
     * Calculate fee without surge (for backward compat).
     */
    public double calculateBaseFee(VehicleType type, LocalDateTime entry, LocalDateTime exit) {
        Duration duration = Duration.between(entry, exit);
        long minutes = duration.toMinutes();
        if (minutes < 1) minutes = 1;
        double hours = Math.ceil(minutes / 30.0) / 2.0;
        if (hours < 1.0) hours = 1.0;
        double rate = surgeService.getBaseRate(type);
        return Math.round(hours * rate * 100.0) / 100.0;
    }

    public double applyDiscount(double amount, double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) return amount;
        return Math.round((amount - (amount * discountPercent / 100)) * 100.0) / 100.0;
    }

    public String generateReceipt(String vehicleNumber, String slotNumber,
                                   LocalDateTime entry, LocalDateTime exit,
                                   double amount, double discount) {
        return generateReceipt(vehicleNumber, slotNumber, entry, exit, amount, discount, "CASH", 0, "", "");
    }

    public String generateReceipt(String vehicleNumber, String slotNumber,
                                   LocalDateTime entry, LocalDateTime exit,
                                   double amount, double discount, String paymentMethod) {
        return generateReceipt(vehicleNumber, slotNumber, entry, exit, amount, discount, paymentMethod, 0, "", "");
    }

    public String generateReceipt(String vehicleNumber, String slotNumber,
                                   LocalDateTime entry, LocalDateTime exit,
                                   double amount, double discount, String paymentMethod,
                                   double fineAmount, String sessionPin, String exitPin) {
        Duration dur = Duration.between(entry, exit);
        double finalAmount = applyDiscount(amount, discount) + fineAmount;

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════╗\n");
        sb.append("║        SNAPPARK — RECEIPT            ║\n");
        sb.append("║     Smart Parking Management         ║\n");
        sb.append("╠══════════════════════════════════════╣\n");
        sb.append(String.format("║  Vehicle  : %-24s ║\n", vehicleNumber));
        sb.append(String.format("║  Slot     : %-24s ║\n", slotNumber));
        sb.append(String.format("║  Entry    : %-24s ║\n", entry.toString().replace("T"," ").substring(0,16)));
        sb.append(String.format("║  Exit     : %-24s ║\n", exit.toString().replace("T"," ").substring(0,16)));
        sb.append(String.format("║  Duration : %dh %dm%22s║\n", dur.toHours(), dur.toMinutesPart(), ""));
        sb.append("╠══════════════════════════════════════╣\n");
        sb.append(String.format("║  Parking  : Rs. %-20.2f ║\n", amount));
        if (discount > 0)
            sb.append(String.format("║  Discount : %-24s ║\n", String.format("%.0f%%", discount)));
        if (fineAmount > 0)
            sb.append(String.format("║  Fines    : Rs. %-20.2f ║\n", fineAmount));
        sb.append("╠══════════════════════════════════════╣\n");
        sb.append(String.format("║  TOTAL    : Rs. %-20.2f ║\n", finalAmount));
        sb.append(String.format("║  Payment  : %-24s ║\n", paymentMethod));
        if (sessionPin != null && !sessionPin.isBlank())
            sb.append(String.format("║  Session# : %-24s ║\n", sessionPin));
        if (exitPin != null && !exitPin.isBlank())
            sb.append(String.format("║  Exit PIN : %-24s ║\n", exitPin));
        sb.append("╠══════════════════════════════════════╣\n");
        sb.append("║     Thank you for using SnapPark!    ║\n");
        sb.append("╚══════════════════════════════════════╝\n");
        return sb.toString();
    }

    public double[] getRates() {
        return new double[]{
            surgeService.getBaseRate(VehicleType.BIKE),
            surgeService.getBaseRate(VehicleType.CAR),
            surgeService.getBaseRate(VehicleType.SUV)
        };
    }
}