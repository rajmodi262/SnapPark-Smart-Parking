package com.snappark.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.snappark.dao.ReportDAO;
import com.snappark.model.VehicleFrequencyRow;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Generates professional PDF reports using OpenPDF.
 * Currently supports: Monthly Vehicle Visit Frequency Report.
 */
public class PdfReportService {
    private static PdfReportService instance;
    private final ReportDAO reportDAO = new ReportDAO();

    private PdfReportService() {}

    public static synchronized PdfReportService getInstance() {
        if (instance == null) instance = new PdfReportService();
        return instance;
    }

    // ── Branded Colors ───────────────────────────────────────────
    private static final Color HEADER_BG    = new Color(13, 21, 37);    // #0D1525
    private static final Color ACCENT_CYAN  = new Color(0, 194, 255);   // #00C2FF
    private static final Color ACCENT_GREEN = new Color(0, 255, 136);   // #00FF88
    private static final Color ROW_ALT      = new Color(241, 245, 249); // #F1F5F9
    private static final Color TEXT_DARK    = new Color(30, 41, 59);     // #1E293B
    private static final Color TEXT_MUTED   = new Color(100, 116, 139);  // #64748B
    private static final Color BORDER_COLOR = new Color(203, 213, 225);  // #CBD5E1

    /**
     * Generate a Monthly Vehicle Visit Frequency PDF report.
     *
     * @param year  4-digit year
     * @param month 1-12
     * @return PDF bytes ready for download, or null on error
     */
    public byte[] generateMonthlyFrequencyReport(int year, int month) {
        // Validate
        if (month < 1 || month > 12 || year < 2000 || year > 2100) return null;

        List<VehicleFrequencyRow> rows = reportDAO.getMonthlyFrequency(year, month);
        String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss"));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── HEADER ───────────────────────────────────────────
            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE);
            Font subFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(148, 163, 184));

            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(HEADER_BG);
            headerCell.setPadding(20);
            headerCell.setBorder(Rectangle.NO_BORDER);

            Paragraph brand = new Paragraph();
            brand.add(new Chunk("\uD83C\uDD7F\uFE0F  SNAPPARK", titleFont));
            brand.add(new Chunk("  — Smart Parking Management", subFont));
            headerCell.addElement(brand);

            Font reportTitleFont = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT_CYAN);
            Paragraph reportTitle = new Paragraph("Monthly Vehicle Visit Frequency Report", reportTitleFont);
            reportTitle.setSpacingBefore(8);
            headerCell.addElement(reportTitle);

            Font metaFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(148, 163, 184));
            Paragraph meta = new Paragraph(
                "Period: " + monthName + " " + year +
                "  |  Generated: " + timestamp +
                "  |  Vehicles: " + rows.size(), metaFont);
            meta.setSpacingBefore(4);
            headerCell.addElement(meta);

            header.addCell(headerCell);
            doc.add(header);
            doc.add(new Paragraph(" ")); // spacer

            // ── TABLE ────────────────────────────────────────────
            if (rows.isEmpty()) {
                Font emptyFont = new Font(Font.HELVETICA, 14, Font.ITALIC, TEXT_MUTED);
                Paragraph empty = new Paragraph(
                    "No records found for " + monthName + " " + year + ".", emptyFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                empty.setSpacingBefore(60);
                doc.add(empty);
            } else {
                PdfPTable table = new PdfPTable(new float[]{8f, 28f, 22f, 18f, 14f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(4);

                // Column headers
                Font colFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
                String[] headers = {"#", "Vehicle Number", "Phone", "Type", "Visits"};
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, colFont));
                    cell.setBackgroundColor(HEADER_BG);
                    cell.setPadding(8);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setBorderColor(BORDER_COLOR);
                    table.addCell(cell);
                }

                // Data rows
                Font dataFont  = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
                Font monoFont  = new Font(Font.COURIER, 9, Font.BOLD, TEXT_DARK);
                Font freqFont  = new Font(Font.HELVETICA, 11, Font.BOLD, ACCENT_CYAN);

                int totalVisits = 0;
                for (int i = 0; i < rows.size(); i++) {
                    VehicleFrequencyRow row = rows.get(i);
                    totalVisits += row.getFrequency();
                    Color bg = (i % 2 == 1) ? ROW_ALT : Color.WHITE;

                    // # column
                    PdfPCell numCell = new PdfPCell(new Phrase(String.valueOf(i + 1), dataFont));
                    numCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    numCell.setBackgroundColor(bg);
                    numCell.setPadding(6);
                    numCell.setBorderColor(BORDER_COLOR);
                    table.addCell(numCell);

                    // Vehicle number
                    PdfPCell plateCell = new PdfPCell(new Phrase(row.getLicensePlate(), monoFont));
                    plateCell.setBackgroundColor(bg);
                    plateCell.setPadding(6);
                    plateCell.setBorderColor(BORDER_COLOR);
                    table.addCell(plateCell);

                    // Phone
                    String phone = row.getPhoneNumber() != null && !row.getPhoneNumber().isBlank()
                        ? row.getPhoneNumber() : "—";
                    PdfPCell phoneCell = new PdfPCell(new Phrase(phone, dataFont));
                    phoneCell.setBackgroundColor(bg);
                    phoneCell.setPadding(6);
                    phoneCell.setBorderColor(BORDER_COLOR);
                    table.addCell(phoneCell);

                    // Type
                    PdfPCell typeCell = new PdfPCell(new Phrase(row.getVehicleType(), dataFont));
                    typeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    typeCell.setBackgroundColor(bg);
                    typeCell.setPadding(6);
                    typeCell.setBorderColor(BORDER_COLOR);
                    table.addCell(typeCell);

                    // Frequency
                    PdfPCell freqCell = new PdfPCell(new Phrase(String.valueOf(row.getFrequency()), freqFont));
                    freqCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    freqCell.setBackgroundColor(bg);
                    freqCell.setPadding(6);
                    freqCell.setBorderColor(BORDER_COLOR);
                    table.addCell(freqCell);
                }

                // ── TOTAL ROW ────────────────────────────────────
                Font totalLabelFont = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_DARK);
                Font totalValueFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(34, 197, 94));

                PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                emptyCell.setBorder(Rectangle.NO_BORDER);

                // Span 4 columns for "TOTAL VISITS"
                PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL VISITS", totalLabelFont));
                totalLabel.setColspan(4);
                totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabel.setPadding(10);
                totalLabel.setBackgroundColor(new Color(240, 253, 244)); // green-50
                totalLabel.setBorderColor(BORDER_COLOR);
                table.addCell(totalLabel);

                PdfPCell totalValue = new PdfPCell(new Phrase(String.valueOf(totalVisits), totalValueFont));
                totalValue.setHorizontalAlignment(Element.ALIGN_CENTER);
                totalValue.setPadding(10);
                totalValue.setBackgroundColor(new Color(240, 253, 244));
                totalValue.setBorderColor(BORDER_COLOR);
                table.addCell(totalValue);

                doc.add(table);
            }

            // ── FOOTER ───────────────────────────────────────────
            doc.add(new Paragraph(" "));
            Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_MUTED);
            Paragraph footer = new Paragraph(
                "This report was auto-generated by SnapPark Parking Management System. " +
                "Data is based on parking_sessions.entry_time for the selected month.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            writer.close();
            System.out.println("[PdfReportService] Generated frequency report for " + monthName + " " + year
                + " (" + rows.size() + " vehicles, " + baos.size() + " bytes)");
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("[PdfReportService] Error generating PDF: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
