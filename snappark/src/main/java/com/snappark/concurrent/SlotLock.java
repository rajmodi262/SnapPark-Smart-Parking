package com.snappark.concurrent;

import java.time.LocalDateTime;

public class SlotLock {
    private final int slotId;
    private final int userId;
    private final LocalDateTime expiryTime;

    public SlotLock(int slotId, int userId, int timeoutSeconds) {
        this.slotId = slotId;
        this.userId = userId;
        this.expiryTime = LocalDateTime.now().plusSeconds(timeoutSeconds);
    }

    public int getSlotId() { return slotId; }
    public int getUserId() { return userId; }
    public LocalDateTime getExpiryTime() { return expiryTime; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}