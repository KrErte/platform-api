package ee.parandiplaan.vault;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InheritancePlanPdfService {

    private final VaultEntryRepository entryRepository;
    private final VaultCategoryRepository categoryRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final EncryptionService encryptionService;

    private static final Color BRAND_GREEN = new Color(0x1B, 0x43, 0x32);
    private static final Color LIGHT_GREEN = new Color(0x2D, 0x6A, 0x4F);
    private static final Color TABLE_HEADER_BG = new Color(0xD8, 0xF3, 0xDC);
    private static final Color TABLE_ALT_BG = new Color(0xF5, 0xF9, 0xF6);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 28, Font.BOLD, BRAND_GREEN);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.GRAY);
    private static final Font HEADING2_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_GREEN);
    private static final Font HEADING3_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, LIGHT_GREEN);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font RED_FONT = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.RED);

    public byte[] generateInheritancePlan(User user, String encryptionKey) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            // === Cover Page ===
            addCoverPage(doc, user);

            // === Table of Contents ===
            doc.newPage();
            addTableOfContents(doc, user);

            // === Vault Content by Category ===
            List<VaultCategory> categories = categoryRepository.findAllByOrderBySortOrderAsc();
            List<VaultEntry> allEntries = entryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            String salt = user.getEncryptionSalt();

            Map<UUID, List<VaultEntry>> entriesByCategory = new LinkedHashMap<>();
            for (VaultEntry entry : allEntries) {
                entriesByCategory
                        .computeIfAbsent(entry.getCategory().getId(), k -> new ArrayList<>())
                        .add(entry);
            }

            for (VaultCategory category : categories) {
                List<VaultEntry> catEntries = entriesByCategory.get(category.getId());
                if (catEntries == null || catEntries.isEmpty()) continue;

                doc.newPage();
                addCategorySection(doc, category, catEntries, encryptionKey, salt);
            }

            // === Trusted Contacts Section ===
            doc.newPage();
            addTrustedContactsSection(doc, user, categories);

            // === Handover Settings Section ===
            addHandoverSettings(doc, user);

            // === Legal Disclaimer ===
            doc.newPage();
            addLegalDisclaimer(doc);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Inheritance plan PDF generation failed for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Parandiplaan PDF genereerimine ebaonnestus", e);
        }
    }

    private void addCoverPage(Document doc, User user) throws DocumentException {
        for (int i = 0; i < 6; i++) doc.add(Chunk.NEWLINE);

        Paragraph title = new Paragraph("Parandiplaan", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph sub = new Paragraph("Digitaalne parandidokument", SUBTITLE_FONT);
        sub.setAlignment(Element.ALIGN_CENTER);
        doc.add(sub);

        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);

        Paragraph name = new Paragraph(user.getFullName(), new Font(Font.HELVETICA, 20, Font.BOLD, LIGHT_GREEN));
        name.setAlignment(Element.ALIGN_CENTER);
        doc.add(name);

        if (user.getEmail() != null) {
            Paragraph email = new Paragraph(user.getEmail(), SMALL_FONT);
            email.setAlignment(Element.ALIGN_CENTER);
            doc.add(email);
        }

        doc.add(Chunk.NEWLINE);

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Paragraph date = new Paragraph("Genereeritud: " + dateStr, SUBTITLE_FONT);
        date.setAlignment(Element.ALIGN_CENTER);
        doc.add(date);

        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);

        LineSeparator line = new LineSeparator();
        line.setLineColor(TABLE_HEADER_BG);
        doc.add(new Chunk(line));

        doc.add(Chunk.NEWLINE);

        Paragraph confidential = new Paragraph(
                "KONFIDENTSIAALNE DOKUMENT", new Font(Font.HELVETICA, 12, Font.BOLD, Color.RED));
        confidential.setAlignment(Element.ALIGN_CENTER);
        doc.add(confidential);

        Paragraph confDesc = new Paragraph(
                "See dokument sisaldab tundlikku isiklikku teavet. " +
                "Hoidke seda turvaliselt ja jagage ainult usaldusisikutega.",
                RED_FONT);
        confDesc.setAlignment(Element.ALIGN_CENTER);
        confDesc.setSpacingBefore(5);
        doc.add(confDesc);
    }

    private void addTableOfContents(Document doc, User user) throws DocumentException {
        Paragraph tocTitle = new Paragraph("Sisukord", HEADING2_FONT);
        tocTitle.setSpacingAfter(15);
        doc.add(tocTitle);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        String[] sections = {
                "1. Tresori sisu kategooriate kaupa",
                "2. Usaldusisikud ja nende juurdepaasuoigused",
                "3. Uleandmise seaded",
                "4. Oiguslik loobumine"
        };

        for (String section : sections) {
            Paragraph p = new Paragraph(section, new Font(Font.HELVETICA, 12, Font.NORMAL, BRAND_GREEN));
            p.setSpacingBefore(8);
            p.setIndentationLeft(20);
            doc.add(p);
        }
    }

    private void addCategorySection(Document doc, VaultCategory category,
                                     List<VaultEntry> entries, String encryptionKey, String salt) throws DocumentException {
        Paragraph catHeader = new Paragraph(category.getIcon() + "  " + category.getNameEt(), HEADING3_FONT);
        catHeader.setSpacingAfter(10);
        doc.add(catHeader);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        line.setLineWidth(1f);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        for (VaultEntry entry : entries) {
            addEntryBlock(doc, entry, encryptionKey, salt);
        }
    }

    @SuppressWarnings("unchecked")
    private void addEntryBlock(Document doc, VaultEntry entry,
                                String encryptionKey, String salt) throws DocumentException {
        String decryptedTitle;
        String decryptedData;
        String decryptedNotes = null;

        try {
            decryptedTitle = encryptionService.decrypt(entry.getTitle(), entry.getTitleIv(), encryptionKey, salt);
            decryptedData = encryptionService.decrypt(entry.getEncryptedData(), entry.getEncryptionIv(), encryptionKey, salt);
            if (entry.getNotesEncrypted() != null && entry.getNotesIv() != null) {
                decryptedNotes = encryptionService.decrypt(entry.getNotesEncrypted(), entry.getNotesIv(), encryptionKey, salt);
            }
        } catch (Exception e) {
            log.warn("Failed to decrypt entry {}: {}", entry.getId(), e.getMessage());
            doc.add(new Paragraph("Kirje dekrupteerimine ebaonnestus", SMALL_FONT));
            return;
        }

        Paragraph entryTitle = new Paragraph(decryptedTitle, BOLD_FONT);
        entryTitle.setSpacingBefore(12);
        entryTitle.setSpacingAfter(5);
        doc.add(entryTitle);

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> fields = mapper.readValue(entry.getCategory().getFieldTemplate(), List.class);
            Map<String, Object> dataMap = mapper.readValue(decryptedData, Map.class);

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

                    PdfPCell labelCell = new PdfPCell(new Phrase(labelEt != null ? labelEt : key, BOLD_FONT));
                    labelCell.setBorderWidth(0.5f);
                    labelCell.setBorderColor(Color.LIGHT_GRAY);
                    labelCell.setBackgroundColor(TABLE_ALT_BG);
                    labelCell.setPadding(5);

                    PdfPCell valueCell = new PdfPCell(new Phrase(value.toString(), BODY_FONT));
                    valueCell.setBorderWidth(0.5f);
                    valueCell.setBorderColor(Color.LIGHT_GRAY);
                    valueCell.setPadding(5);

                    table.addCell(labelCell);
                    table.addCell(valueCell);
                }
                doc.add(table);
            }
        } catch (Exception e) {
            doc.add(new Paragraph(decryptedData, BODY_FONT));
        }

        if (decryptedNotes != null && !decryptedNotes.isBlank()) {
            Paragraph notes = new Paragraph("Markused: " + decryptedNotes, SMALL_FONT);
            notes.setSpacingBefore(3);
            doc.add(notes);
        }

        if (entry.getReminderDate() != null) {
            Paragraph reminder = new Paragraph(
                    "Meeldetuletus: " + entry.getReminderDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    SMALL_FONT);
            doc.add(reminder);
        }
    }

    private void addTrustedContactsSection(Document doc, User user, List<VaultCategory> categories) throws DocumentException {
        Paragraph header = new Paragraph("Usaldusisikud", HEADING2_FONT);
        header.setSpacingAfter(10);
        doc.add(header);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        List<TrustedContact> contacts = trustedContactRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        if (contacts.isEmpty()) {
            doc.add(new Paragraph("Usaldusisikuid pole lisatud.", BODY_FONT));
            return;
        }

        Map<UUID, String> categoryNames = new LinkedHashMap<>();
        for (VaultCategory c : categories) {
            categoryNames.put(c.getId(), c.getNameEt());
        }

        for (TrustedContact contact : contacts) {
            Paragraph name = new Paragraph(contact.getFullName(), HEADING3_FONT);
            name.setSpacingBefore(12);
            doc.add(name);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(90);
            table.setWidths(new float[]{30, 70});
            table.setSpacingBefore(5);
            table.setSpacingAfter(8);

            addTableRow(table, "E-post", contact.getEmail());
            if (contact.getPhone() != null) addTableRow(table, "Telefon", contact.getPhone());
            if (contact.getRelationship() != null) addTableRow(table, "Suhe", contact.getRelationship());
            addTableRow(table, "Juurdepaasuoigus", contact.getAccessLevel());
            addTableRow(table, "Aktiveerimise viis", formatActivationMode(contact));
            addTableRow(table, "Kutse vastu voetud", contact.isInviteAccepted() ? "Jah" : "Ei");

            if ("PARTIAL".equals(contact.getAccessLevel()) && contact.getAllowedCategories() != null) {
                List<String> catNames = new ArrayList<>();
                for (UUID catId : contact.getAllowedCategories()) {
                    catNames.add(categoryNames.getOrDefault(catId, catId.toString()));
                }
                addTableRow(table, "Kategooriad", String.join(", ", catNames));
            }

            doc.add(table);
        }
    }

    private void addHandoverSettings(Document doc, User user) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        Paragraph header = new Paragraph("Uleandmise seaded", HEADING2_FONT);
        header.setSpacingAfter(10);
        doc.add(header);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        List<TrustedContact> contacts = trustedContactRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        boolean hasAutomatic = contacts.stream().anyMatch(c -> "AUTOMATIC".equals(c.getActivationMode()));

        if (hasAutomatic) {
            doc.add(new Paragraph(
                    "Automaatne uleandmine on seadistatud vähemalt uhe usaldusisiku jaoks. " +
                    "Kui kasutaja on teatud arvu paevi tegevusetu, kaivitub uleandmisprotsess automaatselt.",
                    BODY_FONT));
        } else {
            doc.add(new Paragraph(
                    "Koik uleandmised toimuvad manuaalselt — usaldusisik peab esitama paranditustaotluse, " +
                    "mille kasutaja saab kinnitada voi tagasi lukkata.",
                    BODY_FONT));
        }
    }

    private void addLegalDisclaimer(Document doc) throws DocumentException {
        Paragraph header = new Paragraph("Oiguslik loobumine", HEADING2_FONT);
        header.setSpacingAfter(10);
        doc.add(header);

        LineSeparator line = new LineSeparator();
        line.setLineColor(LIGHT_GREEN);
        doc.add(new Chunk(line));
        doc.add(Chunk.NEWLINE);

        String[] paragraphs = {
                "See dokument on genereeritud Parandiplaan platvormi poolt ja on moeldud kasutaja isikliku " +
                "digitaalse parandi dokumendina. Dokument ei ole oiguslikult siduv testament ega " +
                "asenda notariaalselt kinnitatud dokumente.",
                "Parandiplaan OU ei vastuta dokumendis sisalduva teabe tapsuse, taielikkuse ega " +
                "ajakohasuse eest. Kasutaja vastutab ise oma andmete oigsuse eest.",
                "Soovitame seda dokumenti regulaarselt uuendada ja hoida turvaliselt. " +
                "Oluliste oiguslike otsuste puhul konsulteerige alati kvalifitseeritud juristiga.",
                "Koik tresori andmed on krüpteeritud AES-256-GCM algoritmi abil ja neid " +
                "saab dekrüpteerida ainult kasutaja parooliga."
        };

        for (String text : paragraphs) {
            Paragraph p = new Paragraph(text, BODY_FONT);
            p.setSpacingAfter(10);
            p.setIndentationLeft(10);
            doc.add(p);
        }
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBorderWidth(0.5f);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        labelCell.setBackgroundColor(TABLE_ALT_BG);
        labelCell.setPadding(5);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "—", BODY_FONT));
        valueCell.setBorderWidth(0.5f);
        valueCell.setBorderColor(Color.LIGHT_GRAY);
        valueCell.setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatActivationMode(TrustedContact contact) {
        if ("AUTOMATIC".equals(contact.getActivationMode())) {
            return "Automaatne (" + (contact.getInactivityDays() != null ? contact.getInactivityDays() : 90) + " paeva tegevusetust)";
        }
        return "Manuaalne";
    }

    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            String footerText = "Parandiplaan — Konfidentsiaalne | " +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                    " | Lk " + writer.getPageNumber();
            Phrase footer = new Phrase(footerText, FOOTER_FONT);
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER, footer,
                    (document.right() + document.left()) / 2, document.bottom() - 15, 0);
        }
    }
}
