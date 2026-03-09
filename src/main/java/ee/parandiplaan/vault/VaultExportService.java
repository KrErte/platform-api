package ee.parandiplaan.vault;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultExportService {

    private final VaultEntryRepository entryRepository;
    private final VaultCategoryRepository categoryRepository;
    private final EncryptionService encryptionService;

    private static final Color BRAND_GREEN = new Color(0x1B, 0x43, 0x32);
    private static final Color LIGHT_GREEN = new Color(0x2D, 0x6A, 0x4F);
    private static final Color TABLE_HEADER_BG = new Color(0xD8, 0xF3, 0xDC);
    private static final Color TABLE_ALT_BG = new Color(0xF5, 0xF9, 0xF6);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 24, Font.BOLD, BRAND_GREEN);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.GRAY);
    private static final Font CATEGORY_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, LIGHT_GREEN);
    private static final Font ENTRY_TITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font NOTES_FONT = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(0x55, 0x55, 0x55));

    public byte[] exportToPdf(User user, String encryptionKey) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            // Footer with page numbers
            writer.setPageEvent(new PdfFooterEvent());

            document.open();

            // Title page
            addTitlePage(document, user);

            // Load all categories and entries
            List<VaultCategory> categories = categoryRepository.findAllByOrderBySortOrderAsc();
            List<VaultEntry> allEntries = entryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

            // Group entries by category
            Map<UUID, List<VaultEntry>> entriesByCategory = new LinkedHashMap<>();
            for (VaultEntry entry : allEntries) {
                entriesByCategory
                        .computeIfAbsent(entry.getCategory().getId(), k -> new ArrayList<>())
                        .add(entry);
            }

            // Each category section
            for (VaultCategory category : categories) {
                List<VaultEntry> categoryEntries = entriesByCategory.get(category.getId());
                if (categoryEntries == null || categoryEntries.isEmpty()) continue;

                document.newPage();
                addCategorySection(document, category, categoryEntries, encryptionKey);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF export failed for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("PDF eksport ebaonnestus", e);
        }
    }

    private void addTitlePage(Document document, User user) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        Paragraph title = new Paragraph("Parandiplaan", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        document.add(Chunk.NEWLINE);

        Paragraph name = new Paragraph(user.getFullName(), new Font(Font.HELVETICA, 18, Font.NORMAL, LIGHT_GREEN));
        name.setAlignment(Element.ALIGN_CENTER);
        document.add(name);

        document.add(Chunk.NEWLINE);

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Paragraph date = new Paragraph("Genereeritud: " + dateStr, SUBTITLE_FONT);
        date.setAlignment(Element.ALIGN_CENTER);
        document.add(date);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        LineSeparator line = new LineSeparator();
        line.setLineColor(TABLE_HEADER_BG);
        document.add(new Chunk(line));

        document.add(Chunk.NEWLINE);

        Paragraph disclaimer = new Paragraph(
                "See dokument sisaldab tundlikku informatsiooni. Hoidke seda turvaliselt.",
                new Font(Font.HELVETICA, 10, Font.ITALIC, Color.RED));
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        document.add(disclaimer);
    }

    private void addCategorySection(Document document, VaultCategory category,
                                     List<VaultEntry> entries, String encryptionKey) throws DocumentException {
        // Category header
        Paragraph catHeader = new Paragraph(category.getIcon() + "  " + category.getNameEt(), CATEGORY_FONT);
        catHeader.setSpacingAfter(10);
        document.add(catHeader);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        line.setLineWidth(1f);
        document.add(new Chunk(line));
        document.add(Chunk.NEWLINE);

        for (int i = 0; i < entries.size(); i++) {
            VaultEntry entry = entries.get(i);
            addEntryBlock(document, entry, encryptionKey, i);
        }
    }

    private void addEntryBlock(Document document, VaultEntry entry,
                                String encryptionKey, int index) throws DocumentException {
        // Decrypt entry data
        String decryptedTitle;
        String decryptedData;
        String decryptedNotes = null;

        try {
            decryptedTitle = encryptionService.decrypt(entry.getTitle(), entry.getTitleIv(), encryptionKey);
            decryptedData = encryptionService.decrypt(entry.getEncryptedData(), entry.getEncryptionIv(), encryptionKey);
            if (entry.getNotesEncrypted() != null && entry.getNotesIv() != null) {
                decryptedNotes = encryptionService.decrypt(entry.getNotesEncrypted(), entry.getNotesIv(), encryptionKey);
            }
        } catch (Exception e) {
            log.warn("Failed to decrypt entry {}: {}", entry.getId(), e.getMessage());
            Paragraph errorParagraph = new Paragraph("Kirje dekrupteerimine ebaonnestus", NOTES_FONT);
            document.add(errorParagraph);
            return;
        }

        // Entry title
        Paragraph entryTitle = new Paragraph(decryptedTitle, ENTRY_TITLE_FONT);
        entryTitle.setSpacingBefore(index > 0 ? 15 : 5);
        entryTitle.setSpacingAfter(5);
        document.add(entryTitle);

        // Parse JSON data into field table
        try {
            // Data is a JSON string — parse the key/value pairs
            String fieldTemplate = entry.getCategory().getFieldTemplate();
            List<Map<String, Object>> fields = parseFieldTemplate(fieldTemplate);
            Map<String, Object> dataMap = parseJsonData(decryptedData);

            if (!dataMap.isEmpty()) {
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(95);
                table.setWidths(new float[]{35, 65});
                table.setSpacingAfter(5);

                for (Map<String, Object> field : fields) {
                    String key = (String) field.get("key");
                    String labelEt = (String) field.get("label_et");
                    Object value = dataMap.get(key);

                    if (value == null || value.toString().isBlank()) continue;

                    PdfPCell labelCell = new PdfPCell(new Phrase(labelEt != null ? labelEt : key, LABEL_FONT));
                    labelCell.setBorderWidth(0.5f);
                    labelCell.setBorderColor(Color.LIGHT_GRAY);
                    labelCell.setBackgroundColor(TABLE_ALT_BG);
                    labelCell.setPadding(5);

                    PdfPCell valueCell = new PdfPCell(new Phrase(value.toString(), VALUE_FONT));
                    valueCell.setBorderWidth(0.5f);
                    valueCell.setBorderColor(Color.LIGHT_GRAY);
                    valueCell.setPadding(5);

                    table.addCell(labelCell);
                    table.addCell(valueCell);
                }

                document.add(table);
            }
        } catch (Exception e) {
            // Fallback: just add raw data
            Paragraph rawData = new Paragraph(decryptedData, VALUE_FONT);
            document.add(rawData);
        }

        // Notes
        if (decryptedNotes != null && !decryptedNotes.isBlank()) {
            Paragraph notesLabel = new Paragraph("Markused:", LABEL_FONT);
            notesLabel.setSpacingBefore(3);
            document.add(notesLabel);

            Paragraph notes = new Paragraph(decryptedNotes, NOTES_FONT);
            notes.setSpacingAfter(5);
            document.add(notes);
        }

        // Reminder date
        if (entry.getReminderDate() != null) {
            Paragraph reminder = new Paragraph(
                    "Meeldetuletus: " + entry.getReminderDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    NOTES_FONT);
            document.add(reminder);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFieldTemplate(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonData(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * PDF footer event handler — adds page number and generation date to each page.
     */
    private static class PdfFooterEvent extends com.lowagie.text.pdf.PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            String footerText = "Genereeritud " +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                    " - Parandiplaan | Lk " + writer.getPageNumber();

            Phrase footer = new Phrase(footerText, FOOTER_FONT);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    footer,
                    (document.right() + document.left()) / 2,
                    document.bottom() - 15,
                    0
            );
        }
    }
}
