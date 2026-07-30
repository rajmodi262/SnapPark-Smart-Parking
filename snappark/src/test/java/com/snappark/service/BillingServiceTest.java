package com.snappark.service;

import com.snappark.model.enums.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for billing: half-hour rounding, loyalty tiers and discounts. */
class BillingServiceTest {

    private final BillingService svc = BillingService.getInstance();
    private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    void baseFeeRoundsUpToHalfHourBlocks() {
        // 45 min → ceil(45/30)/2 = 1.0 h  → 1.0 * 50 = 50.0
        assertEquals(50.0, svc.calculateBaseFee(VehicleType.CAR, ENTRY, ENTRY.plusMinutes(45)));
        // 90 min → ceil(90/30)/2 = 1.5 h  → 1.5 * 50 = 75.0
        assertEquals(75.0, svc.calculateBaseFee(VehicleType.CAR, ENTRY, ENTRY.plusMinutes(90)));
        // 10 min → floors to the 1.0 h minimum → 50.0
        assertEquals(50.0, svc.calculateBaseFee(VehicleType.CAR, ENTRY, ENTRY.plusMinutes(10)));
    }

    @Test
    void loyaltyDiscountTiers() {
        assertEquals(10.0, svc.calculateLoyaltyDiscount(ENTRY.minusHours(12), ENTRY)); // within 24h
        assertEquals(5.0,  svc.calculateLoyaltyDiscount(ENTRY.minusDays(3), ENTRY));   // within 7d
        assertEquals(2.0,  svc.calculateLoyaltyDiscount(ENTRY.minusDays(10), ENTRY));  // within 30d
        assertEquals(0.0,  svc.calculateLoyaltyDiscount(ENTRY.minusDays(40), ENTRY));  // lapsed
        assertEquals(0.0,  svc.calculateLoyaltyDiscount(null, ENTRY));                 // first visit
    }

    @Test
    void applyDiscountRejectsInvalidPercentages() {
        assertEquals(90.0,  svc.applyDiscount(100.0, 10.0)); // valid
        assertEquals(100.0, svc.applyDiscount(100.0, -5.0)); // negative → ignored
        assertEquals(100.0, svc.applyDiscount(100.0, 150.0)); // > 100 → ignored
    }

    @Test
    void loyaltyLabelsAndRates() {
        assertEquals("Returning within 24hrs", svc.getLoyaltyLabel(10.0));
        assertEquals("", svc.getLoyaltyLabel(0.0));
        assertArrayEquals(new double[]{20.0, 50.0, 80.0}, svc.getRates());
    }
}
