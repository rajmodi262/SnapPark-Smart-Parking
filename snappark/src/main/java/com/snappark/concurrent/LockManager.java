package com.snappark.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {
    private static LockManager instance;
    private final ConcurrentHashMap<Integer, SlotLock> locks = new ConcurrentHashMap<>();
    private static final int LOCK_TIMEOUT_SECONDS = 300; // 5 minutes

    private LockManager() {}

    public static synchronized LockManager getInstance() {
        if (instance == null) {
            instance = new LockManager();
        }
        return instance;
    }

    // Returns true if lock was acquired, false if slot already locked
    public boolean acquireLock(int slotId, int userId) {
        SlotLock newLock = new SlotLock(slotId, userId, LOCK_TIMEOUT_SECONDS);
        SlotLock existing = locks.putIfAbsent(slotId, newLock);
        if (existing != null && existing.isExpired()) {
            // Old lock expired, replace it
            locks.put(slotId, newLock);
            return true;
        }
        return existing == null;
    }

    public boolean releaseLock(int slotId, int userId) {
        SlotLock lock = locks.get(slotId);
        if (lock != null && lock.getUserId() == userId) {
            locks.remove(slotId);
            return true;
        }
        return false;
    }

    public boolean isLocked(int slotId) {
        SlotLock lock = locks.get(slotId);
        if (lock == null) return false;
        if (lock.isExpired()) {
            locks.remove(slotId);
            return false;
        }
        return true;
    }

    public SlotLock getLock(int slotId) {
        return locks.get(slotId);
    }

    // Returns list of expired lock slot IDs
    public List<Integer> getExpiredLocks() {
        List<Integer> expired = new ArrayList<>();
        locks.forEach((slotId, lock) -> {
            if (lock.isExpired()) expired.add(slotId);
        });
        return expired;
    }

    public void removeExpiredLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public int getLockedCount() {
        return locks.size();
    }
}