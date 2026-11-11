package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Builds the GST tax invoice (or Bill of Supply when no GST) as a PDF.
 * Uses the built-in Helvetica font, so amounts are written "Rs." instead of the rupee sign.
 */
@Service
public class InvoicePdfService {

    private static final Color BRAND = new Color(76, 29, 149);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color LIGHT = new Color(243, 240, 250);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
    private final Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private final Font whiteBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

    public byte[] render(Bill bill) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        // Same PAID / UNPAID / OVERDUE stamp the website shows over the invoice
        writer.setPageEvent(new StatusWatermark(bill.getStatus()));
        doc.open();

        boolean gst = bill.getGstRate() != null;
        // HSN/SAC codes only matter when GST is charged; left out for No GST and 0%
        boolean showHsn = gst && bill.getGstRate().signum() != 0;

        // Header: seller on the left, invoice number and dates on the right
        PdfPTable header = new PdfPTable(new float[]{3, 2});
        header.setWidthPercentage(100);
        PdfPCell seller = cell(null);
        seller.addElement(new Paragraph(gst ? "TAX INVOICE" : "BILL OF SUPPLY", heading));
        seller.addElement(new Paragraph(orDash(bill.getSupplierName()), title));
        if (StringUtils.hasText(bill.getSupplierAddress())) seller.addElement(new Paragraph(bill.getSupplierAddress(), normal));
        if (StringUtils.hasText(bill.getSupplierGstin())) seller.addElement(new Paragraph("GSTIN: " + bill.getSupplierGstin(), normal));
        if (StringUtils.hasText(bill.getSupplierStateCode())) seller.addElement(new Paragraph("State code: " + bill.getSupplierStateCode(), normal));
        header.addCell(seller);

        PdfPCell meta = cell(null);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.addElement(right(new Paragraph("Invoice " + bill.getId(), bold)));
        meta.addElement(right(new Paragraph("Date: " + (bill.getCreatedAt() != null ? bill.getCreatedAt().format(DATE) : "-"), normal)));
        meta.addElement(right(new Paragraph("Due: " + (bill.getDueDate() != null ? bill.getDueDate().format(DATE) : "-"), normal)));
        meta.addElement(right(new Paragraph("Status: " + bill.getStatus(), bold)));
        header.addCell(meta);
        doc.add(header);
        doc.add(spacer());

        // Bill to
        PdfPTable to = new PdfPTable(1);
        to.setWidthPercentage(100);
        PdfPCell toCell = cell(LIGHT);
        toCell.setPadding(8);
        toCell.addElement(new Paragraph("BILL TO", heading));
        toCell.addElement(new Paragraph(bill.getCustomerName(), bold));
        if (StringUtils.hasText(bill.getCustomerGstin())) toCell.addElement(new Paragraph("GSTIN: " + bill.getCustomerGstin(), normal));
        toCell.addElement(new Paragraph("Place of supply: " + orDash(bill.getPlaceOfSupply()), normal));
        to.addCell(toCell);
        doc.add(to);
        doc.add(spacer());

        // Items
        PdfPTable items = showHsn
                ? new PdfPTable(new float[]{0.5f, 4, 1.4f, 1, 1.6f, 1.8f})
                : new PdfPTable(new float[]{0.5f, 5.4f, 1, 1.6f, 1.8f});
        items.setWidthPercentage(100);
        String[] headers = showHsn
                ? new String[]{"#", "Description", "HSN/SAC", "Qty", "Rate", "Amount"}
                : new String[]{"#", "Description", "Qty", "Rate", "Amount"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, whiteBold));
            c.setBackgroundColor(BRAND);
            c.setPadding(5);
            c.setBorder(Rectangle.NO_BORDER);
            if (!h.equals("Description")) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            items.addCell(c);
        }
        int n = 1;
        for (BillItem item : bill.getItems()) {
            items.addCell(row(String.valueOf(n++), true));
            items.addCell(row(item.getName(), false));
            if (showHsn) items.addCell(row(orDash(item.getHsnSac()), true));
            items.addCell(row(plain(item.getQuantity()), true));
            items.addCell(row(money(item.getPrice()), true));
            items.addCell(row(money(item.getAmount()), true));
        }
        doc.add(items);
        doc.add(spacer());

        // Totals
        PdfPTable totals = new PdfPTable(new float[]{3, 2});
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        total(totals, gst ? "Taxable value" : "Subtotal", bill.getSubtotal(), false);
        if (gst) {
            BigDecimal half = bill.getGstRate().divide(BigDecimal.valueOf(2));
            if (bill.isInterState()) {
                total(totals, "IGST (" + plain(bill.getGstRate()) + "%)", bill.getIgst(), false);
            } else {
                total(totals, "CGST (" + plain(half) + "%)", bill.getCgst(), false);
                total(totals, "SGST (" + plain(half) + "%)", bill.getSgst(), false);
            }
        }
        total(totals, "Total", bill.getTotal(), true);
        doc.add(totals);

        doc.add(new Paragraph("Amount in words: " + orDash(bill.getAmountInWords()), normal));
        if (!gst) doc.add(new Paragraph("GST not applicable.", small));
        if (StringUtils.hasText(bill.getNotes())) {
            doc.add(spacer());
            doc.add(new Paragraph("Notes", heading));
            doc.add(new Paragraph(bill.getNotes(), normal));
        }
        doc.add(spacer());
        doc.add(new Paragraph("This is a computer-generated invoice.", small));

        doc.close();
        return out.toByteArray();
    }

    private void total(PdfPTable t, String label, BigDecimal amount, boolean grand) {
        Font f = grand ? whiteBold : normal;
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        PdfPCell v = new PdfPCell(new Phrase(money(amount), f));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(4);
            if (grand) c.setBackgroundColor(BRAND);
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private PdfPCell row(String text, boolean alignRight) {
        PdfPCell c = new PdfPCell(new Phrase(text, normal));
        c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(229, 231, 235));
        if (alignRight) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell cell(Color background) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        if (background != null) c.setBackgroundColor(background);
        return c;
    }

    private Paragraph right(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private String money(BigDecimal v) {
        return "Rs. " + new DecimalFormat("#,##0.00").format(v != null ? v : BigDecimal.ZERO);
    }

    private String plain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private String orDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }

    /**
     * Draws the status stamp (PAID green, UNPAID amber, OVERDUE red) across every page,
     * matching .ubs-status-watermark on the website: faint bordered text tilted 30 degrees.
     */
    static class StatusWatermark extends PdfPageEventHelper {
        private static final float ANGLE_DEG = 30f;
        private static final float TEXT_OPACITY = 0.13f;
        private static final float BORDER_OPACITY = 0.16f;

        private final String text;
        private final Color color;

        StatusWatermark(Bill.BillStatus status) {
            if (status == Bill.BillStatus.PAID) {
                text = "PAID";
                color = new Color(5, 150, 105);
            } else if (status == Bill.BillStatus.OVERDUE) {
                text = "OVERDUE";
                color = new Color(220, 38, 38);
            } else {
                text = "UNPAID";
                color = new Color(217, 119, 6);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            BaseFont font;
            try {
                font = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                return; // a missing watermark must never stop the invoice from rendering
            }
            Rectangle page = document.getPageSize();

            // Size the word so the stamp spans about 60% of the page width, capped like the website
            float letterSpacingEm = 0.12f;
            int gaps = Math.max(text.length() - 1, 0);
            float widthPerPoint = font.getWidthPoint(text, 1f) + letterSpacingEm * gaps;
            float size = Math.min(110f, page.getWidth() * 0.6f / widthPerPoint);
            float spacing = size * letterSpacingEm;
            float textWidth = font.getWidthPoint(text, size) + spacing * gaps;
            float capHeight = font.getFontDescriptor(BaseFont.CAPHEIGHT, size);

            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();

            // Rotate around the page centre; everything below is drawn centred on (0, 0)
            double rad = Math.toRadians(ANGLE_DEG);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            cb.concatCTM(cos, sin, -sin, cos, page.getWidth() / 2f, page.getHeight() / 2f);

            PdfGState state = new PdfGState();
            state.setFillOpacity(TEXT_OPACITY);
            state.setStrokeOpacity(BORDER_OPACITY);
            cb.setGState(state);

            // Rounded border around the word
            float padX = size * 0.3f;
            float padY = size * 0.18f;
            cb.setColorStroke(color);
            cb.setLineWidth(size * 0.06f);
            cb.roundRectangle(-textWidth / 2f - padX, -capHeight / 2f - padY,
                    textWidth + 2 * padX, capHeight + 2 * padY, size * 0.12f);
            cb.stroke();

            // The word itself
            cb.beginText();
            cb.setFontAndSize(font, size);
            cb.setCharacterSpacing(spacing);
            cb.setColorFill(color);
            cb.setTextMatrix(-textWidth / 2f, -capHeight / 2f);
            cb.showText(text);
            cb.endText();

            cb.restoreState();
        }
    }
}
