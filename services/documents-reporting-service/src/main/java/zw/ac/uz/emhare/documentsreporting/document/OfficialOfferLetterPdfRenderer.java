package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;

/** Renders governed UZ offer letters from immutable projections. @author Tinashe K */
@Component
public class OfficialOfferLetterPdfRenderer {
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM uuuu").withZone(ZoneId.of("Africa/Harare"));

    public OfficialResultSlipPdfRenderer.RenderedPdf render(GeneratedDocument document, OfferLetterProjection offer) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Admission Offer - " + offer.getOfferNumber()); info.setAuthor("Tinashe K");
            info.setSubject("eMhare governed admission offer"); info.setKeywords(document.getDocumentNumber());
            pdf.setDocumentInformation(info);
            PDPage page = new PDPage(PDRectangle.A4); pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                float width = page.getMediaBox().getWidth();
                content.setNonStrokingColor(0f, 91f/255f, 65f/255f); content.addRect(0, 735, width, 107); content.fill();
                text(content, BOLD, 20, 44, 804, "UNIVERSITY OF ZIMBABWE");
                text(content, REGULAR, 9, 44, 785, "Admissions Office | eMhare");
                text(content, BOLD, 15, 44, 754, "OFFICIAL ADMISSION OFFER");
                content.setNonStrokingColor(28f/255f,37f/255f,42f/255f);
                text(content, REGULAR, 9, 44, 706, "Offer number: " + safe(offer.getOfferNumber(), 45));
                text(content, REGULAR, 9, 350, 706, "Application: " + safe(offer.getApplicationNumber(), 30));
                text(content, REGULAR, 11, 44, 665, "Dear " + safe(offer.getApplicantName(), 65) + ",");
                text(content, REGULAR, 10, 44, 635, "The University of Zimbabwe is pleased to offer you admission to:");
                content.setNonStrokingColor(245f/255f,247f/255f,246f/255f); content.addRect(44, 554, width-88, 58); content.fill();
                content.setNonStrokingColor(0f,91f/255f,65f/255f);
                text(content, BOLD, 14, 58, 585, safe(offer.getProgrammeName(), 63));
                text(content, REGULAR, 9, 58, 568, "Programme " + safe(offer.getProgrammeCode(), 25) + " | " + offer.getOfferType() + " offer");
                content.setNonStrokingColor(28f/255f,37f/255f,42f/255f);
                int y = 520;
                text(content, BOLD, 9, 44, y, "COMMENCEMENT"); text(content, REGULAR, 10, 180, y, offer.getCommencementDate().toString());
                y -= 26; text(content, BOLD, 9, 44, y, "ACCEPT BY"); text(content, REGULAR, 10, 180, y, DATE.format(offer.getAcceptanceDeadline()));
                if (offer.getRegistrationDate()!=null) { y-=26; text(content,BOLD,9,44,y,"REGISTRATION"); text(content,REGULAR,10,180,y,offer.getRegistrationDate().toString()); }
                if (offer.getOrientationDate()!=null) { y-=26; text(content,BOLD,9,44,y,"ORIENTATION"); text(content,REGULAR,10,180,y,offer.getOrientationDate().toString()); }
                y -= 42; text(content, BOLD, 10, 44, y, "Conditions");
                y -= 22; text(content, REGULAR, 9, 44, y, safe(offer.getConditionsText()==null ? "No additional conditions." : offer.getConditionsText(), 95));
                y -= 50; text(content, REGULAR, 9, 44, y, "Accept or decline this offer through your authenticated eMhare applicant workspace.");
                text(content, BOLD, 10, 44, 110, "Admissions Office");
                text(content, REGULAR, 8, 44, 82, "This offer is valid only while its status remains active in eMhare.");
                content.setNonStrokingColor(87f/255f,96f/255f,101f/255f);
                text(content, REGULAR, 7, 44, 36, "System-generated official document. Verify using " + safe(document.getDocumentNumber(), 55) + ".");
            }
            pdf.save(output); return new OfficialResultSlipPdfRenderer.RenderedPdf(output.toByteArray(), 1);
        } catch (IOException exception) { throw new IllegalStateException("Official offer-letter PDF could not be rendered.", exception); }
    }
    private void text(PDPageContentStream c, PDType1Font f, float s, float x, float y, String v) throws IOException {
        c.beginText(); c.setFont(f,s); c.newLineAtOffset(x,y); c.showText(v.replaceAll("[^\\x20-\\x7E]","?")); c.endText();
    }
    private String safe(String v, int max) { if(v==null)return ""; String s=v.trim(); return s.length()<=max?s:s.substring(0,max-3)+"..."; }
}
