package com.snappark.concurrent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.snappark.dao.SlotDAO;
import com.snappark.model.enums.SlotStatus;

public class LockSweeper {
    private static LockSweeper instance;
    private final ScheduledExecutorService scheduler;
    private final LockManager lockManager;
    private final SlotDAO slotDAO;
    private Runnable onLockExpired; // UI callback

    private LockSweeper() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.lockManager = LockManager.getInstance();
        this.slotDAO = new SlotDAO();
    }

    public static LockSweeper getInstance() {
        if (instance == null) {
            instance = new LockSweeper();
        }
        return instance;
    }

    public void setOnLockExpired(Runnable callback) {
        this.onLockExpired = callback;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            List<Integer> expired = lockManager.getExpiredLocks();
            for (int slotId : expired) {
                slotDAO.updateStatus(slotId, SlotStatus.AVAILABLE);
                System.out.println("Lock expired for slot: " + slotId);
            }
            lockManager.removeExpiredLocks();
            if (!expired.isEmpty() && onLockExpired != null) {
                onLockExpired.run(); // Notify UI
            }
        }, 30, 30, TimeUnit.SECONDS);
        System.out.println("LockSweeper started!");
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}