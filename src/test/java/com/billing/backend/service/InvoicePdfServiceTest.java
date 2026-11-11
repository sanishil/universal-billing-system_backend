package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePdfServiceTest {

    private final InvoicePdfService service = new InvoicePdfService();

    private static Bill bill(Bill.BillStatus status) {
        BillItem item = BillItem.builder().name("Consulting").quantity(BigDecimal.ONE)
                .price(new BigDecimal("3500")).amount(new BigDecimal("3500")).build();
        return Bill.builder().id("INV-1001").customerName("Sample Customer").status(status)
                .gstRate(null).subtotal(new BigDecimal("3500")).total(new BigDecimal("3500"))
                .items(List.of(item)).build();
    }

    @ParameterizedTest
    @CsvSource({"PAID, PAID", "PENDING, UNPAID", "OVERDUE, OVERDUE"})
    void stampsStatusWatermark(Bill.BillStatus status, String expectedWord) throws IOException {
        byte[] pdf = service.render(bill(status));

        // Set -Dinvoice.pdf.out=<dir> to save the PDFs and look at them
        String outDir = System.getProperty("invoice.pdf.out");
        if (outDir != null) Files.write(Path.of(outDir, "invoice-" + status + ".pdf"), pdf);

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        // The body only prints "Status: PENDING", so UNPAID can only come from the watermark
        assertTrue(text.contains(expectedWord), () -> "watermark '" + expectedWord + "' missing from: " + text);
    }
}
