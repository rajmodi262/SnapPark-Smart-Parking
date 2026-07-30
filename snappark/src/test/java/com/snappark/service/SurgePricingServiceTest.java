package com.snappark.service;

import com.snappark.model.enums.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the dynamic surge-pricing engine (time + occupancy tiers). */
class SurgePricingServiceTest {

    private final SurgePricingService svc = SurgePricingService.getInstance();

    @Test
    void baseRatesMatchTariff() {
        assertEquals(20.0, svc.getBaseRate(VehicleType.BIKE));
        assertEquals(50.0, svc.getBaseRate(VehicleType.CAR));
        assertEquals(80.0, svc.getBaseRate(VehicleType.SUV));
    }

    @Test
    void timeMultiplierTiers() {
        assertEquals(1.00, svc.getTimeMultiplier(LocalTime.of(6, 0)));  // Early Bird
        assertEquals(1.20, svc.getTimeMultiplier(LocalTime.of(9, 0)));  // Morning Peak
        assertEquals(1.00, svc.getTimeMultiplier(LocalTime.of(13, 0))); // Standard
        assertEquals(1.30, svc.getTimeMultiplier(LocalTime.of(18, 0))); // Rush Hour
        assertEquals(0.90, svc.getTimeMultiplier(LocalTime.of(22, 0))); // Night Discount
        assertEquals(0.85, svc.getTimeMultiplier(LocalTime.of(2, 0)));  // Late Night
    }

    @Test
    void occupancyMultiplierTiers() {
        assertEquals(1.00, svc.getOccupancyMultiplier(0, 0));    // guard: empty lot
        assertEquals(1.25, svc.getOccupancyMultiplier(95, 100)); // >= 90% almost full
        assertEquals(1.15, svc.getOccupancyMultiplier(80, 100)); // >= 75% high demand
        assertEquals(1.05, svc.getOccupancyMultiplier(60, 100)); // >= 50% moderate
        assertEquals(1.00, svc.getOccupancyMultiplier(10, 100)); // normal
    }

    @Test
    void breakdownComputesEffectiveRateAndFlags() {
        // Rush hour + almost full → surge on a car (50 * 1.30 * 1.25 = 81.25)
        var surge = svc.getBreakdown(VehicleType.CAR, 95, 100, LocalTime.of(18, 0));
        assertEquals(81.25, surge.effectiveRate, 1e-9);
        assertTrue(surge.isSurge);
        assertFalse(surge.isDiscount);

        // Late night + empty → discount on a car (50 * 0.85 * 1.00 = 42.5)
        var discount = svc.getBreakdown(VehicleType.CAR, 0, 100, LocalTime.of(2, 0));
        assertEquals(42.5, discount.effectiveRate, 1e-9);
        assertTrue(discount.isDiscount);
        assertFalse(discount.isSurge);

        // Standard midday + empty → neutral (50 * 1.0 * 1.0 = 50.0)
        var neutral = svc.getBreakdown(VehicleType.CAR, 0, 100, LocalTime.of(13, 0));
        assertEquals(50.0, neutral.effectiveRate, 1e-9);
        assertEquals(0, neutral.getSurgePercent());
        assertFalse(neutral.isSurge);
        assertFalse(neutral.isDiscount);
    }
}
