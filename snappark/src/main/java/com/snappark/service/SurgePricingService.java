package com.snappark.service;

import com.snappark.model.enums.VehicleType;
import java.time.LocalTime;

public class SurgePricingService {
    private static SurgePricingService instance;
    private SurgePricingService() {}
    public static synchronized SurgePricingService getInstance() {
        if (instance == null) instance = new SurgePricingService();
        return instance;
    }

    // Base rates per hour
    private static final double BIKE_BASE = 20.0;
    private static final double CAR_BASE  = 50.0;
    private static final double SUV_BASE  = 80.0;

    public static class PriceBreakdown {
        public final double baseRate;
        public final double timeMultiplier;
        public final double occupancyMultiplier;
        public final double effectiveRate;
        public final String timeLabel;
        public final String occupancyLabel;
        public final boolean isSurge;
        public final boolean isDiscount;

        public PriceBreakdown(double baseRate, double timeMult, double occMult,
                              String timeLabel, String occLabel) {
            this.baseRate = baseRate;
            this.timeMultiplier = timeMult;
            this.occupancyMultiplier = occMult;
            this.effectiveRate = Math.round(baseRate * timeMult * occMult * 100.0) / 100.0;
            this.timeLabel = timeLabel;
            this.occupancyLabel = occLabel;
            this.isSurge = (timeMult * occMult) > 1.0;
            this.isDiscount = (timeMult * occMult) < 1.0;
        }

        public int getSurgePercent() {
            return (int) Math.round((timeMultiplier * occupancyMultiplier - 1.0) * 100);
        }
    }

    public double getBaseRate(VehicleType type) {
        return switch (type) {
            case BIKE -> BIKE_BASE;
            case CAR  -> CAR_BASE;
            case SUV  -> SUV_BASE;
        };
    }

    /**
     * Time-of-day multiplier (6 tiers).
     */
    public double getTimeMultiplier() {
        return getTimeMultiplier(LocalTime.now());
    }

    public double getTimeMultiplier(LocalTime time) {
        int h = time.getHour();
        if (h >= 5  && h < 8)  return 1.00; // Early Bird
        if (h >= 8  && h < 12) return 1.20; // Morning Peak
        if (h >= 12 && h < 17) return 1.00; // Standard
        if (h >= 17 && h < 21) return 1.30; // Rush Hour
        if (h >= 21)           return 0.90; // Night Discount
        return 0.85;                          // Late Night (0-5)
    }

    public String getTimeLabel() {
        return getTimeLabel(LocalTime.now());
    }

    public String getTimeLabel(LocalTime time) {
        int h = time.getHour();
        if (h >= 5  && h < 8)  return "Early Bird";
        if (h >= 8  && h < 12) return "⚡ Morning Peak (+20%)";
        if (h >= 12 && h < 17) return "Standard";
        if (h >= 17 && h < 21) return "🔥 Rush Hour (+30%)";
        if (h >= 21)           return "🌙 Night Discount (-10%)";
        return "🌙 Late Night (-15%)";
    }

    /**
     * Occupancy-based multiplier (4 tiers).
     */
    public double getOccupancyMultiplier(long occupied, long total) {
        if (total == 0) return 1.0;
        double pct = (double) occupied / total * 100;
        if (pct >= 90) return 1.25;  // Almost Full
        if (pct >= 75) return 1.15;  // High Demand
        if (pct >= 50) return 1.05;  // Moderate
        return 1.00;                  // Normal
    }

    public String getOccupancyLabel(long occupied, long total) {
        if (total == 0) return "Normal";
        double pct = (double) occupied / total * 100;
        if (pct >= 90) return "🔴 Almost Full (+25%)";
        if (pct >= 75) return "🟠 High Demand (+15%)";
        if (pct >= 50) return "🟡 Moderate (+5%)";
        return "🟢 Normal";
    }

    /**
     * Full price breakdown for display.
     */
    public PriceBreakdown getBreakdown(VehicleType type, long occupied, long total) {
        double base = getBaseRate(type);
        double timeMult = getTimeMultiplier();
        double occMult = getOccupancyMultiplier(occupied, total);
        return new PriceBreakdown(base, timeMult, occMult, getTimeLabel(), getOccupancyLabel(occupied, total));
    }

    public PriceBreakdown getBreakdown(VehicleType type, long occupied, long total, LocalTime time) {
        double base = getBaseRate(type);
        double timeMult = getTimeMultiplier(time);
        double occMult = getOccupancyMultiplier(occupied, total);
        return new PriceBreakdown(base, timeMult, occMult, getTimeLabel(time), getOccupancyLabel(occupied, total));
    }
}
