package com.snappark.service;

import java.util.List;

import com.snappark.concurrent.LockManager;
import com.snappark.dao.SlotDAO;
import com.snappark.model.ParkingSlot;
import com.snappark.model.enums.SlotStatus;

public class ParkingService {
    private static ParkingService instance;
    private final SlotDAO slotDAO;
    private final LockManager lockManager;

    private ParkingService() {
        this.slotDAO = new SlotDAO();
        this.lockManager = LockManager.getInstance();
    }

    public static ParkingService getInstance() {
        if (instance == null) {
            instance = new ParkingService();
        }
        return instance;
    }

    public boolean lockSlot(int slotId, int userId) {
        ParkingSlot slot = slotDAO.findById(slotId);
        if (slot == null || slot.getStatus() != SlotStatus.AVAILABLE) {
            System.out.println("Slot not available: " + slotId);
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

    public boolean confirmBooking(int slotId, int userId) {
        if (!lockManager.isLocked(slotId)) return false;
        if (lockManager.getLock(slotId).getUserId() != userId) return false;
        slotDAO.updateStatus(slotId, SlotStatus.OCCUPIED);
        lockManager.releaseLock(slotId, userId);
        System.out.println("Booking confirmed for slot: " + slotId);
        return true;
    }

    public boolean releaseSlot(int slotId) {
        slotDAO.updateStatus(slotId, SlotStatus.AVAILABLE);
        System.out.println("Slot released: " + slotId);
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
}