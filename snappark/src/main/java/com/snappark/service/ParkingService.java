package com.snappark.service;

import java.util.List;

import com.snappark.concurrent.LockManager;
import com.snappark.dao.SessionDAO;
import com.snappark.dao.SlotDAO;
import com.snappark.model.ParkingSlot;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;

public class ParkingService {
    private static ParkingService instance;
    private final SlotDAO slotDAO;
    private final SessionDAO sessionDAO;
    private final LockManager lockManager;

    private ParkingService() {
        this.slotDAO = new SlotDAO();
        this.sessionDAO = new SessionDAO();
        this.lockManager = LockManager.getInstance();
    }

    public static synchronized ParkingService getInstance() {
        if (instance == null) {
            instance = new ParkingService();
        }
        return instance;
    }

    public boolean lockSlot(int slotId, int userId) {
        ParkingSlot slot = slotDAO.findById(slotId);
        if (slot == null) {
            System.out.println("Slot not found: " + slotId);
            return false;
        }
        // Allow locking if slot is AVAILABLE, or SHARED (for second bike)
        if (slot.getStatus() != SlotStatus.AVAILABLE && slot.getStatus() != SlotStatus.SHARED) {
            System.out.println("Slot not available: " + slotId + " (status=" + slot.getStatus() + ")");
            return false;
        }
        boolean locked = lockManager.acquireLock(slotId, userId);
        if (locked) {
            slotDAO.updateStatus(slotId, SlotStatus.LOCKING);
            System.out.println("Slot locked: " + slotId + " by user: " + userId);
        } else {
            System.out.println("Slot already locked: " + slotId);
        }
        return locked;
    }

    /**
     * Confirm booking for a normal slot (set to OCCUPIED).
     */
    public boolean confirmBooking(int slotId, int userId) {
        if (!lockManager.isLocked(slotId)) return false;
        if (lockManager.getLock(slotId).getUserId() != userId) return false;
        slotDAO.updateStatus(slotId, SlotStatus.OCCUPIED);
        lockManager.releaseLock(slotId, userId);
        System.out.println("Booking confirmed for slot: " + slotId);
        return true;
    }

    /**
     * Confirm booking for a shared slot (first bike → SHARED, second bike → OCCUPIED).
     * @param isSharedBooking true if this is a bike parking in a car slot
     * @param existingSessions how many active sessions already exist on this slot
     */
    public boolean confirmSharedBooking(int slotId, int userId, int existingSessions) {
        if (!lockManager.isLocked(slotId)) return false;
        if (lockManager.getLock(slotId).getUserId() != userId) return false;

        if (existingSessions == 0) {
            // First bike in car slot → SHARED (room for one more)
            slotDAO.updateStatus(slotId, SlotStatus.SHARED);
            System.out.println("Shared booking (1st bike) confirmed for slot: " + slotId);
        } else {
            // Second bike → OCCUPIED (slot full)
            slotDAO.updateStatus(slotId, SlotStatus.OCCUPIED);
            System.out.println("Shared booking (2nd bike) confirmed for slot: " + slotId);
        }
        lockManager.releaseLock(slotId, userId);
        return true;
    }

    /**
     * Release a slot. Checks if there are remaining sessions (for shared slots).
     * If one bike is still parked, sets SHARED. Otherwise, AVAILABLE.
     */
    public boolean releaseSlot(int slotId) {
        int remaining = sessionDAO.countActiveBySlot(slotId);
        if (remaining > 0) {
            // Another bike is still parked in this shared slot
            slotDAO.updateStatus(slotId, SlotStatus.SHARED);
            System.out.println("Slot partially released (shared): " + slotId + " (" + remaining + " session(s) remaining)");
        } else {
            slotDAO.updateStatus(slotId, SlotStatus.AVAILABLE);
            System.out.println("Slot released: " + slotId);
        }
        return true;
    }

    public List<ParkingSlot> getAllSlots() {
        return slotDAO.findAll();
    }

    public List<ParkingSlot> getSlotsByFloor(int floor) {
        return slotDAO.findByFloor(floor);
    }

    public long getAvailableCount() {
        return slotDAO.findAll().stream()
            .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
            .count();
    }

    public long getOccupiedCount() {
        return slotDAO.findAll().stream()
            .filter(s -> s.getStatus() == SlotStatus.OCCUPIED)
            .count();
    }

    /**
     * Check if all BIKE-type slots are full (OCCUPIED or LOCKING).
     * Used to determine if bikes should be offered CAR slots for sharing.
     */
    public boolean areBikeSlotsFull() {
        List<ParkingSlot> allSlots = slotDAO.findAll();
        long totalBikeSlots = allSlots.stream()
            .filter(s -> s.getType() == VehicleType.BIKE)
            .count();
        long availableBikeSlots = allSlots.stream()
            .filter(s -> s.getType() == VehicleType.BIKE && s.getStatus() == SlotStatus.AVAILABLE)
            .count();
        return totalBikeSlots > 0 && availableBikeSlots == 0;
    }

    /**
     * Check if all CAR-type slots are full (OCCUPIED, LOCKING, or SHARED).
     * Used to determine if cars should be offered SUV slots for overflow.
     */
    public boolean areCarSlotsFull() {
        List<ParkingSlot> allSlots = slotDAO.findAll();
        long totalCarSlots = allSlots.stream()
            .filter(s -> s.getType() == VehicleType.CAR)
            .count();
        long availableCarSlots = allSlots.stream()
            .filter(s -> s.getType() == VehicleType.CAR && s.getStatus() == SlotStatus.AVAILABLE)
            .count();
        return totalCarSlots > 0 && availableCarSlots == 0;
    }
}