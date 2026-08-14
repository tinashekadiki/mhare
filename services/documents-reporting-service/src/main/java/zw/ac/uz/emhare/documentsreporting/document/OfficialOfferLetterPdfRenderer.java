package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayOutputStream;
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
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot;
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

    public OfficialOfferLetterPdfRenderer() {
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
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("INSTITUTION_NAME", first(snapshot.institutionName(), "University of Zimbabwe"));
        values.put("INSTITUTION_CONTACT", contact(snapshot));
        values.put("DOCUMENT_TITLE", "OFFICIAL ADMISSION OFFER");
        values.put("OFFER_REFERENCE", "Offer: " + first(offer.getOfferNumber(), "-")
                + "    Application: " + first(offer.getApplicationNumber(), "-"));
        values.put("ISSUE_DATE", document.getRequestedAt() == null ? "" : "Issued: " + INSTANT_DATE.format(document.getRequestedAt()));
        values.put("APPLICANT_ADDRESS", first(offer.getApplicantName(), "Applicant") + "\n"
                + first(snapshot.applicantPostalAddress(), offer.getApplicantEmail()));
        values.put("SALUTATION", "Dear " + first(offer.getApplicantName(), "Applicant") + ",");
        values.put("OPENING_PARAGRAPH", openingParagraph(offer, snapshot));
        values.put("PROGRAMME_DETAILS", programmeDetails(offer, snapshot));
        values.put("SCHEDULE_DETAILS", scheduleDetails(offer));
        values.put("CONDITIONS", conditions(offer));
        values.put("RESPONSE_INSTRUCTION", "Accept or decline this offer by "
                + INSTANT_DATE.format(offer.getAcceptanceDeadline())
                + " through your authenticated eMhare applicant workspace. The portal response is the authoritative record.");
        values.put("SIGNATORY", first(snapshot.signatoryName(), "Registrar") + "\n"
                + first(snapshot.signatoryTitle(), "Registrar"));
        values.put("DOCUMENT_NUMBER", "Verification number: " + document.getDocumentNumber());
        values.put("REQUIRED_EVIDENCE", evidence(snapshot.requiredVerificationDocuments()));
        values.put("FEE_SCHEDULE", feeSchedule(snapshot.feeSchedule()));
        values.put("PAYMENT_INSTRUCTION", paymentInstruction(snapshot.feeSchedule()));
        values.put("STANDARD_TERMS", standardTerms());
        values.put("POLICY_VERSION", "Content policy: " + first(snapshot.contentPolicyVersion(), "LEGACY-V2"));
        return values;
    }

    private OfferLetterContentSnapshot content(OfferLetterProjection offer) {
        if (offer.getContentSnapshot() != null) return offer.getContentSnapshot();
        return new OfferLetterContentSnapshot("University of Zimbabwe", "University of Zimbabwe", null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), null, "Registrar", "Registrar", "LEGACY-V2");
    }

    private String openingParagraph(OfferLetterProjection offer, OfferLetterContentSnapshot snapshot) {
        String applicantKind = isInternational(snapshot.applicantCategoryCode()) ? " International applicant." : "";
        return "The University is pleased to offer you " + offer.getOfferType().toLowerCase(Locale.ROOT)
                + " admission to the programme shown below." + applicantKind
                + " This letter records the approved offer terms at the time this document version was generated.";
    }

    private String programmeDetails(OfferLetterProjection offer, OfferLetterContentSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        line(value, "Programme", offer.getProgrammeName() + " (" + offer.getProgrammeCode() + ")");
        line(value, "Award", snapshot.awardName());
        line(value, "Academic unit", snapshot.academicUnitName());
        line(value, "Application route", first(snapshot.applicationRouteName(), snapshot.applicationRouteCode()));
        line(value, "Programme level", snapshot.programmeLevelName());
        line(value, "Intake", snapshot.intakeName());
        line(value, "Study option", joined(snapshot.studyOptions()));
        line(value, "Programme version", snapshot.programmeVersionCode());
        return value.toString().strip();
    }

    private String scheduleDetails(OfferLetterProjection offer) {
        StringBuilder value = new StringBuilder();
        line(value, "Commencement", date(offer.getCommencementDate()));
        line(value, "Registration", date(offer.getRegistrationDate()));
        line(value, "Orientation", date(offer.getOrientationDate()));
        return value.toString().strip();
    }

    private String conditions(OfferLetterProjection offer) {
        return "CONDITIONS\n" + first(offer.getConditionsText(), "No additional conditions apply to this offer.");
    }

    private String evidence(List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "Bring the original identity and academic evidence listed in your eMhare application workspace."
                    + " Uploaded copies remain subject to verification.";
        }
        return requirements.stream().map(value -> "• " + value).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String feeSchedule(FeeScheduleSnapshot schedule) {
        if (schedule == null || schedule.lines().isEmpty()) {
            return "Finance has not supplied a published fee snapshot for this letter. No fee amount is created or implied"
                    + " by this document. Consult the authenticated eMhare Finance workspace before making payment.";
        }
        String currency = first(schedule.transactionCurrencyCode(), "USD").toUpperCase(Locale.ROOT);
        StringBuilder value = new StringBuilder("Published Finance schedule ")
                .append(first(schedule.financeFeeStructureCode(), "-")).append(" (version ")
                .append(schedule.financeFeeStructureVersion()).append(")\n\n");
        for (FeeLineSnapshot line : schedule.lines()) {
            value.append(first(line.description(), line.code())).append("    ")
                    .append(currency).append(' ').append(money(line.transactionAmount())).append('\n');
        }
        value.append("\nTOTAL    ").append(currency).append(' ').append(money(schedule.transactionTotal()));
        if (!"USD".equals(currency) && schedule.baseTotal() != null) {
            value.append("\nUSD base equivalent    USD ").append(money(schedule.baseTotal()));
        }
        return value.toString();
    }

    private String paymentInstruction(FeeScheduleSnapshot schedule) {
        String rating = schedule != null && schedule.exchangeRateId() != null
                ? " Any non-USD conversion is backed by Finance exchange-rate evidence in this snapshot."
                : "";
        return "Use only payment methods and references displayed in your authenticated eMhare Finance workspace."
                + " This letter never substitutes bank details or assumes an exchange rate." + rating;
    }

    private String standardTerms() {
        return "1. Admission remains subject to verification of the original evidence listed above.\n\n"
                + "2. The offer applies only to the programme, route, intake and study options recorded in this letter.\n\n"
                + "3. Registration is completed only after all academic, identity and Finance requirements are satisfied.\n\n"
                + "4. The current published document in eMhare supersedes earlier versions.\n\n"
                + "5. The University may withdraw an offer obtained using false, incomplete or misleading information.";
    }

    private String contact(OfferLetterContentSnapshot snapshot) {
        return joined(java.util.Arrays.asList(snapshot.institutionPostalAddress(), snapshot.institutionTelephone(),
                snapshot.institutionEmail(), snapshot.institutionWebsite()).stream()
                .filter(value -> value != null && !value.isBlank()).toList());
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

    private void line(StringBuilder target, String label, String value) {
        if (value != null && !value.isBlank()) target.append(label).append(":  ").append(value.trim()).append('\n');
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
    private boolean isInternational(String category) {
        if (category == null) return false;
        String normalized = category.toUpperCase(Locale.ROOT);
        return normalized.contains("FOREIGN") || normalized.contains("INTERNATIONAL");
    }
}
