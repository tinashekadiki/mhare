package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.BankAccountSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeLineSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;

/** Renders the single governed UZ offer-letter design from immutable inputs. @author Tinashe K */
@Component
public class OfficialOfferLetterPdfRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter INSTANT_DATE = DATE.withZone(ZoneId.of("Africa/Harare"));
    private final JasperReport report;
    private final OfferLetterSignatureLoader signatureLoader;

    public OfficialOfferLetterPdfRenderer() {
        this(documentId -> null);
    }

    @Autowired
    public OfficialOfferLetterPdfRenderer(OfferLetterSignatureLoader signatureLoader) {
        this.signatureLoader = signatureLoader;
        report = UzOfferLetterJasperTemplate.compile();
    }

    public OfficialResultSlipPdfRenderer.RenderedPdf render(GeneratedDocument document, OfferLetterProjection offer) {
        try {
            OfferLetterContentSnapshot snapshot = content(offer);
            JasperPrint print = JasperFillManager.fillReport(report, parameters(document, offer, snapshot),
                    new JREmptyDataSource(1));
            byte[] generatedPdf = JasperExportManager.exportReportToPdf(print);
            byte[] officialPdf = addMetadata(generatedPdf, document, offer);
            return new OfficialResultSlipPdfRenderer.RenderedPdf(officialPdf, print.getPages().size());
        } catch (Exception exception) {
            throw new IllegalStateException("Official offer-letter PDF could not be rendered.", exception);
        }
    }

    private Map<String, Object> parameters(GeneratedDocument document, OfferLetterProjection offer,
            OfferLetterContentSnapshot snapshot) {
        if (offer.getOfferType() == null) {
            throw new IllegalArgumentException("An offer type is required to render an offer letter.");
        }
        FeeColumns feeColumns = feeColumns(snapshot.feeSchedule());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("LOGO", logo());
        values.put("SIGNATURE_IMAGE", signatureLoader.load(snapshot.signatorySignatureDocumentId()));
        values.put("INSTITUTION_ADDRESS", institutionAddress(snapshot));
        values.put("INSTITUTION_TITLE", first(snapshot.institutionName(), "University of Zimbabwe").toUpperCase(Locale.ROOT));
        values.put("ISSUE_DATE", document.getRequestedAt() == null ? "" : INSTANT_DATE.format(document.getRequestedAt()));
        values.put("APPLICANT_ADDRESS", applicantAddress(offer, snapshot));
        values.put("SALUTATION", "Dear " + first(offer.getApplicantName(), "Applicant") + ",");
        values.put("ADMISSION_HEADING", admissionHeading(document, offer, snapshot));
        values.put("OPENING_PARAGRAPH", "I am pleased to inform you that a place has been reserved for you on the above mentioned degree programme.");
        values.put("REGISTRATION_INSTRUCTION", registrationInstruction(offer, snapshot));
        values.put("FEE_INTRODUCTION", feeIntroduction(snapshot.feeSchedule()));
        values.put("FEE_ITEMS_1", feeColumns.items().get(0));
        values.put("FEE_ITEMS_2", feeColumns.items().get(1));
        values.put("FEE_ITEMS_3", feeColumns.items().get(2));
        values.put("FEE_AMOUNTS_1", feeColumns.amounts().get(0));
        values.put("FEE_AMOUNTS_2", feeColumns.amounts().get(1));
        values.put("FEE_AMOUNTS_3", feeColumns.amounts().get(2));
        values.put("FEE_TOTAL", feeTotal(snapshot.feeSchedule()));
        values.put("PAYMENT_INSTRUCTION", paymentInstruction(snapshot));
        values.put("COMMENCEMENT_INSTRUCTION", commencementInstruction(offer));
        values.put("EQUALITY_TERM", "The University is a non-discriminatory and inclusive equal opportunity institution and students are expected to attend all official learning schedules and activities as prescribed. The University academic business runs 24 hours 7 days a week during a defined semester.");
        values.put("AMENDMENT_TERM", "Kindly note that this offer is made without prejudice to the rights which the University may have to amend, withdraw or cancel in the event of you or the University being unable to meet the conditions of the offer.");
        values.put("REGISTRATION_TERM", "Admittance to the University is made subject to your accepting the conditions set in this letter and registering in the stipulated period. Failure to do so will lead to the University withdrawing your name from its list of successful applicants for the " + academicYear(document, offer) + " Academic year.");
        values.put("BLENDED_LEARNING_TERM", "The University of Zimbabwe offers blended learning and each student is required to bring their own Laptop/Tablet to support their eLearning activities.");
        values.put("CONGRATULATIONS", "Congratulations on being accepted to the University of Zimbabwe. We wish you all the best in your academic journey.");
        values.put("SIGNATORY", first(snapshot.signatoryName(), "Registrar") + "\n"
                + first(snapshot.signatoryTitle(), "Registrar"));
        values.put("ACCEPTANCE_INSTRUCTION", acceptanceInstruction(offer, snapshot.feeSchedule()));
        values.put("ACCEPTANCE_DECLARATION", "B. I accept/do not accept the university offer as outlined in this letter. By accepting this offer I confirm that the programme offered and my personal details are correct.");
        values.put("DOCUMENT_NUMBER", "Verification number: " + document.getDocumentNumber());
        return values;
    }

    private OfferLetterContentSnapshot content(OfferLetterProjection offer) {
        if (offer.getContentSnapshot() != null) return offer.getContentSnapshot();
        return new OfferLetterContentSnapshot("University of Zimbabwe", "University of Zimbabwe", null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), null, "Registrar", "Registrar", "LEGACY-V2");
    }

    private InputStream logo() {
        InputStream logo = OfficialOfferLetterPdfRenderer.class.getResourceAsStream("/documents/uz-logo.jpg");
        if (logo == null) throw new IllegalStateException("The University of Zimbabwe offer-letter logo is missing.");
        return logo;
    }

    private String institutionAddress(OfferLetterContentSnapshot snapshot) {
        String address = first(snapshot.institutionPostalAddress(), "P.O.Box MP 167, Mount Pleasant, Harare, Zimbabwe");
        return first(snapshot.institutionName(), "University of Zimbabwe") + "\n" + address.replace(", ", "\n");
    }

    private String applicantAddress(OfferLetterProjection offer, OfferLetterContentSnapshot snapshot) {
        return first(offer.getApplicantName(), "Applicant") + " (" + first(offer.getApplicationNumber(), "-") + ")\n"
                + first(snapshot.applicantPostalAddress(), offer.getApplicantEmail());
    }

    private String admissionHeading(GeneratedDocument document, OfferLetterProjection offer,
            OfferLetterContentSnapshot snapshot) {
        String academicUnit = snapshot.academicUnitName() == null || snapshot.academicUnitName().isBlank()
                ? "" : " IN THE " + snapshot.academicUnitName().trim();
        return "ADMISSION IN THE YEAR " + academicYear(document, offer) + " TO THE "
                + first(offer.getProgrammeName(), "PROGRAMME") + " (" + first(offer.getProgrammeCode(), "-") + ")"
                + academicUnit;
    }

    private String registrationInstruction(OfferLetterProjection offer, OfferLetterContentSnapshot snapshot) {
        String registration = offer.getRegistrationDate() == null
                ? "Registration arrangements will be communicated through your authenticated eMhare applicant workspace."
                : "Registration commences on " + date(offer.getRegistrationDate()) + ".";
        String evidence = snapshot.requiredVerificationDocuments().isEmpty()
                ? "your original identity and academic evidence"
                : "the originals of " + joined(snapshot.requiredVerificationDocuments());
        String conditions = offer.getConditionsText() == null || offer.getConditionsText().isBlank()
                ? "" : " Conditions of this offer: " + offer.getConditionsText().trim();
        return registration + " Use your application number when completing online registration through your authenticated eMhare workspace. You are also expected to bring "
                + evidence + " for verification at the Faculty Office." + conditions;
    }

    private String feeIntroduction(FeeScheduleSnapshot schedule) {
        if (schedule == null || schedule.lines().isEmpty()) {
            return "Finance has not supplied a published fee snapshot for this letter. No fee amount is created or implied"
                    + " by this document. Consult the authenticated eMhare Finance workspace before making payment."
                    + " PLEASE NOTE THAT FEES ARE SUBJECT TO CHANGE WITHOUT NOTICE.";
        }
        return "The fees payable per semester are as indicated below. The fees are payable in "
                + schedule.transactionCurrencyCode() + ". PLEASE NOTE THAT FEES ARE SUBJECT TO CHANGE WITHOUT NOTICE.";
    }

    private FeeColumns feeColumns(FeeScheduleSnapshot schedule) {
        List<FeeLineSnapshot> lines = schedule == null || schedule.lines().isEmpty()
                ? legacyFeeHeadings() : schedule.lines();
        int rowsPerColumn = Math.max(1, (lines.size() + 2) / 3);
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        java.util.ArrayList<String> amounts = new java.util.ArrayList<>();
        for (int column = 0; column < 3; column++) {
            int start = Math.min(column * rowsPerColumn, lines.size());
            int end = Math.min(start + rowsPerColumn, lines.size());
            items.add(lines.subList(start, end).stream().map(line -> first(line.description(), line.code()))
                    .reduce((left, right) -> left + "\n" + right).orElse(""));
            amounts.add(lines.subList(start, end).stream()
                    .map(line -> schedule == null || schedule.lines().isEmpty()
                            ? "-" : amount(schedule, line.transactionAmount()))
                    .reduce((left, right) -> left + "\n" + right).orElse(""));
        }
        return new FeeColumns(items, amounts);
    }

    private List<FeeLineSnapshot> legacyFeeHeadings() {
        return List.of(
                new FeeLineSnapshot("TUITION", "Tuition Fees", null, null),
                new FeeLineSnapshot("MEDICAL", "Medical Fees", null, null),
                new FeeLineSnapshot("EXAMINATION", "Examination Fees", null, null),
                new FeeLineSnapshot("DEVELOPMENT", "Student Development Levy", null, null),
                new FeeLineSnapshot("REGISTRATION", "Registration Fees", null, null),
                new FeeLineSnapshot("STUDENT_UNION", "Student Union Levy", null, null),
                new FeeLineSnapshot("LABORATORY", "Laboratory Fees", null, null),
                new FeeLineSnapshot("SPORTS", "Sports Fees", null, null),
                new FeeLineSnapshot("INDUSTRIALISATION", "Industrialisation Levy", null, null),
                new FeeLineSnapshot("MAINTENANCE", "Maintenance Fees", null, null),
                new FeeLineSnapshot("TECHNOLOGY", "Technology Fees", null, null),
                new FeeLineSnapshot("ATTACHMENT", "Attachment Fees", null, null));
    }

    private String feeTotal(FeeScheduleSnapshot schedule) {
        if (schedule == null || schedule.lines().isEmpty()) return "TOTAL    -";
        String total = "TOTAL    " + amount(schedule, schedule.transactionTotal());
        if (!"USD".equals(schedule.transactionCurrencyCode()) && schedule.baseTotal() != null) {
            total += "    USD base equivalent    USD " + money(schedule.baseTotal());
        }
        return total;
    }

    private String amount(FeeScheduleSnapshot schedule, BigDecimal value) {
        return schedule.transactionCurrencyCode() + " " + money(value);
    }

    private String paymentInstruction(OfferLetterContentSnapshot snapshot) {
        FeeScheduleSnapshot schedule = snapshot.feeSchedule();
        String rating = schedule != null && schedule.exchangeRateId() != null
                ? " Any non-USD conversion is backed by Finance exchange-rate evidence in this snapshot."
                : "";
        if (!snapshot.bankAccounts().isEmpty()) {
            Map<String, List<BankAccountSnapshot>> accountsByBank = new LinkedHashMap<>();
            for (BankAccountSnapshot account : snapshot.bankAccounts()) {
                accountsByBank.computeIfAbsent(account.bankName().trim(), ignored -> new java.util.ArrayList<>())
                        .add(account);
            }
            String configuredAccounts = accountsByBank.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue().stream()
                            .map(account -> account.currencyCode() + " " + account.accountNumber().trim())
                            .reduce((left, right) -> left + " | " + right).orElse(""))
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            String references = snapshot.bankAccounts().stream()
                    .map(BankAccountSnapshot::paymentReferenceInstructions)
                    .filter(value -> !isBlank(value)).map(String::trim).distinct()
                    .reduce((left, right) -> left + " " + right).orElse("");
            return configuredAccounts + (references.isBlank() ? "" : ". " + references) + rating;
        }
        if (snapshot.bankDetails() == null || isBlank(snapshot.bankDetails().bankName())
                || isBlank(snapshot.bankDetails().accountNumber())) {
            return "Bank details have not been configured in the Institution Profile. Use only payment methods and references displayed in your authenticated eMhare Finance workspace." + rating;
        }
        var bank = snapshot.bankDetails();
        String details = "BANK: " + bank.bankName().trim()
                + " | CURRENCY: NOT RECORDED | ACCOUNT NUMBER: " + bank.accountNumber().trim();
        return details + optional(". ", bank.paymentReferenceInstructions()) + rating;
    }

    private String optional(String prefix, String value) {
        return isBlank(value) ? "" : prefix + value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String commencementInstruction(OfferLetterProjection offer) {
        if (offer.getCommencementDate() == null) {
            return "Lecture commencement details will be communicated through your authenticated eMhare workspace.";
        }
        return "Lectures commence on " + date(offer.getCommencementDate())
                + " for those who would have completed the registration process.";
    }

    private String acceptanceInstruction(OfferLetterProjection offer, FeeScheduleSnapshot schedule) {
        String deposit = schedule == null ? "" : schedule.lines().stream()
                .filter(line -> line.code() != null && line.code().toUpperCase(Locale.ROOT).contains("DEPOSIT"))
                .findFirst().map(line -> " and pay the non-refundable deposit of " + amount(schedule, line.transactionAmount()))
                .orElse("");
        return "A. To confirm acceptance of your offer, use your authenticated eMhare applicant workspace"
                + deposit + " by " + INSTANT_DATE.format(offer.getAcceptanceDeadline()) + ".";
    }

    private int academicYear(GeneratedDocument document, OfferLetterProjection offer) {
        if (offer.getCommencementDate() != null) return offer.getCommencementDate().getYear();
        if (document.getRequestedAt() != null) return document.getRequestedAt().atZone(ZoneId.of("Africa/Harare")).getYear();
        return LocalDate.now(ZoneId.of("Africa/Harare")).getYear();
    }

    private byte[] addMetadata(byte[] generatedPdf, GeneratedDocument document, OfferLetterProjection offer) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(generatedPdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle("Admission Offer - " + offer.getOfferNumber());
            information.setAuthor("Tinashe K");
            information.setSubject("eMhare governed admission offer");
            information.setKeywords(document.getDocumentNumber());
            pdf.setDocumentInformation(information);
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private String joined(List<String> values) {
        return values == null ? "" : values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? (fallback == null ? "" : fallback.trim()) : preferred.trim();
    }

    private String date(LocalDate value) { return value == null ? null : DATE.format(value); }
    private String money(BigDecimal value) { return value == null ? "-" : String.format(Locale.US, "%,.2f", value); }

    private record FeeColumns(List<String> items, List<String> amounts) { }
}
