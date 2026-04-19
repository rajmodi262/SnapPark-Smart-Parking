package com.snappark.service;

import com.snappark.dao.FineDAO;
import com.snappark.model.Fine;
import java.time.LocalDateTime;
import java.util.List;

public class FineService {
    private static FineService instance;
    private final FineDAO fineDAO = new FineDAO();

    private static final double WRONG_PARKING_FINE = 200.0;
    private static final double OVERTIME_FINE_PER_HR = 100.0;
    private static final double UNAUTHORIZED_EXIT_FINE = 500.0;

    private FineService() {}
    public static FineService getInstance() {
        if (instance == null) instance = new FineService();
        return instance;
    }

    public Fine reportWrongParking(int sessionId, String assignedSlot, String actualSlot,
                                    int reportedBy, String notes) {
        Fine fine = new Fine();
        fine.setSessionId(sessionId);
        fine.setFineType(Fine.FineType.WRONG_PARKING);
        fine.setAmount(WRONG_PARKING_FINE);
        fine.setAssignedSlot(assignedSlot);
        fine.setActualSlot(actualSlot);
        fine.setReportedBy(reportedBy);
        fine.setReportedAt(LocalDateTime.now());
        fine.setPaid(false);
        fine.setNotes(notes != null ? notes : "Vehicle parked at wrong slot");
        return fineDAO.save(fine);
    }

    public Fine reportOvertime(int sessionId, double hours, int reportedBy) {
        double amount = Math.ceil(hours) * OVERTIME_FINE_PER_HR;
        Fine fine = new Fine();
        fine.setSessionId(sessionId);
        fine.setFineType(Fine.FineType.OVERTIME);
        fine.setAmount(amount);
        fine.setReportedBy(reportedBy);
        fine.setReportedAt(LocalDateTime.now());
        fine.setPaid(false);
        fine.setNotes("Overtime: " + String.format("%.1f", hours) + " hours beyond limit");
        return fineDAO.save(fine);
    }

    public Fine reportDamage(int sessionId, double amount, int reportedBy, String notes) {
        Fine fine = new Fine();
        fine.setSessionId(sessionId);
        fine.setFineType(Fine.FineType.DAMAGE);
        fine.setAmount(amount);
        fine.setReportedBy(reportedBy);
        fine.setReportedAt(LocalDateTime.now());
        fine.setPaid(false);
        fine.setNotes(notes);
        return fineDAO.save(fine);
    }

    public List<Fine> getUnpaidFines(int sessionId) {
        return fineDAO.findUnpaidBySession(sessionId);
    }

    public double getUnpaidTotal(int sessionId) {
        return fineDAO.getUnpaidTotal(sessionId);
    }

    public List<Fine> getAllFines(int sessionId) {
        return fineDAO.findBySession(sessionId);
    }

    public void markAllPaid(int sessionId) {
        fineDAO.markPaid(sessionId);
    }

    public boolean hasUnpaidFines(int sessionId) {
        return fineDAO.getUnpaidTotal(sessionId) > 0;
    }

    public double getWrongParkingFineAmount() { return WRONG_PARKING_FINE; }
    public double getOvertimeFinePerHour() { return OVERTIME_FINE_PER_HR; }
}
