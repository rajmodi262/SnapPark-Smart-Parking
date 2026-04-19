package com.snappark.model;

import java.time.LocalDateTime;

public class Fine {
    public enum FineType { WRONG_PARKING, OVERTIME, UNAUTHORIZED_EXIT, DAMAGE }

    private int id;
    private int sessionId;
    private FineType fineType;
    private double amount;
    private String assignedSlot;
    private String actualSlot;
    private int reportedBy;
    private LocalDateTime reportedAt;
    private boolean paid;
    private String notes;

    public Fine() {}

    public Fine(int id, int sessionId, FineType fineType, double amount,
                String assignedSlot, String actualSlot, int reportedBy,
                LocalDateTime reportedAt, boolean paid, String notes) {
        this.id = id;
        this.sessionId = sessionId;
        this.fineType = fineType;
        this.amount = amount;
        this.assignedSlot = assignedSlot;
        this.actualSlot = actualSlot;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
        this.paid = paid;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public FineType getFineType() { return fineType; }
    public void setFineType(FineType fineType) { this.fineType = fineType; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getAssignedSlot() { return assignedSlot; }
    public void setAssignedSlot(String assignedSlot) { this.assignedSlot = assignedSlot; }
    public String getActualSlot() { return actualSlot; }
    public void setActualSlot(String actualSlot) { this.actualSlot = actualSlot; }
    public int getReportedBy() { return reportedBy; }
    public void setReportedBy(int reportedBy) { this.reportedBy = reportedBy; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getFineTypeLabel() {
        return switch (fineType) {
            case WRONG_PARKING     -> "Wrong Parking";
            case OVERTIME          -> "Overtime";
            case UNAUTHORIZED_EXIT -> "Unauthorized Exit";
            case DAMAGE            -> "Infrastructure Damage";
        };
    }
}
