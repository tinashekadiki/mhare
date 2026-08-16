package zw.ac.uz.emhare.documentsreporting.document;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.BankAccountSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeLineSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.BankDetailsSnapshot;

/** @author Tinashe K */
class OfficialOfferLetterPdfRendererTest {
    @Test
    void rendersGovernedOfferLetterWithVerificationMetadata() throws Exception {
        GeneratedDocument document=mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-OF-2028-000001");
        when(document.getRequestedAt()).thenReturn(Instant.parse("2028-01-10T08:00:00Z"));
        OfferLetterProjection offer=mock(OfferLetterProjection.class);
        when(offer.getOfferNumber()).thenReturn("OF-2028-000001");
        when(offer.getApplicationNumber()).thenReturn("EMH-2028-000001");
        when(offer.getApplicantName()).thenReturn("Nyasha Moyo");
        when(offer.getProgrammeName()).thenReturn("Bachelor of Science in Computer Science");
        when(offer.getProgrammeCode()).thenReturn("BSC-CS");
        when(offer.getOfferType()).thenReturn("FIRM");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(offer.getRegistrationDate()).thenReturn(LocalDate.parse("2028-02-26"));
        when(offer.getOrientationDate()).thenReturn(LocalDate.parse("2028-02-29"));
        when(offer.getContentSnapshot()).thenReturn(new OfferLetterContentSnapshot(
                "University of Zimbabwe", "University of Zimbabwe", "P.O. Box MP167, Mount Pleasant, Harare",
                "+263 24 2303211", "admissions@uz.ac.zw", "www.uz.ac.zw", "14 Samora Machel Avenue, Harare",
                "LOCAL", "UNDERGRAD", "Undergraduate", "March 2028 Intake", "Faculty of Science",
                "Bachelor of Science Honours", "Undergraduate", "2028.1", List.of("Computer Science"),
                List.of("National identity document", "Original academic certificates"),
                new FeeScheduleSnapshot(UUID.randomUUID(), 4, "UG-SCI-2028", "USD", "USD", null, null,
                        List.of(new FeeLineSnapshot("TUIT", "Tuition", new BigDecimal("1200.00"), new BigDecimal("1200.00")),
                                new FeeLineSnapshot("REG", "Registration", new BigDecimal("50.00"), new BigDecimal("50.00"))),
                        new BigDecimal("1250.00"), new BigDecimal("1250.00")),
                new BankDetailsSnapshot("CBZ BANK", "KWAME NKRUMAH AVENUE, HARARE", "University of Zimbabwe",
                        "01120770100042/52", "6101", "COBZZWHAXXX", "Use your application number as the payment reference"),
                List.of(
                        new BankAccountSnapshot("ZWG", "CBZ BANK", "KWAME NKRUMAH AVENUE, HARARE",
                                "University of Zimbabwe", "01120770100052", "6101", "COBZZWHAXXX",
                                "After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference."),
                        new BankAccountSnapshot("USD", "CBZ BANK", "KWAME NKRUMAH AVENUE, HARARE",
                                "University of Zimbabwe", "01120770100249", "6101", "COBZZWHAXXX",
                                "After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference."),
                        new BankAccountSnapshot("ZWG", "BancABC", null, "University of Zimbabwe",
                                "10099183902014", null, null,
                                "After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference."),
                        new BankAccountSnapshot("USD", "BancABC", null, "University of Zimbabwe",
                                "10099186633010", null, null,
                                "After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference.")),
                "42b272b2-6f22-4fad-baf2-cfc3d6438e76", "Dr Registrar", "Registrar",
                "UZ-OFFER-LETTER-2026-01"));

        var rendered=new OfficialOfferLetterPdfRenderer(documentId -> OfficialOfferLetterPdfRendererTest.class
                .getResourceAsStream("/documents/uz-logo.jpg")).render(document,offer);

        assertTrue(rendered.content().length>1_000); assertEquals(1,rendered.pageCount());
        try(var pdf=Loader.loadPDF(rendered.content())){
            assertEquals("Tinashe K",pdf.getDocumentInformation().getAuthor());
            assertTrue(java.util.stream.StreamSupport.stream(
                    pdf.getPage(0).getResources().getXObjectNames().spliterator(), false).count() >= 2);
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("UNIVERSITY OF ZIMBABWE"));
            assertTrue(text.contains("ADMISSION IN THE YEAR 2028 TO THE Bachelor of Science in Computer Science (BSC-CS) IN THE Faculty of Science"));
            assertTrue(text.contains("a place has been reserved for you"));
            assertTrue(text.contains("Nyasha Moyo"));
            assertTrue(text.contains("BSC-CS"));
            assertTrue(text.contains("Faculty of Science"));
            assertTrue(text.contains("Computer Science"));
            assertTrue(text.contains("Original academic certificates"));
            assertTrue(text.contains("Cost Item"));
            assertTrue(text.contains("Tuition"));
            assertTrue(text.contains("USD 1,250.00"));
            assertTrue(text.contains("CBZ BANK: ZWG 01120770100052 | USD 01120770100249"));
            assertTrue(text.contains("BancABC: ZWG 10099183902014 | USD 10099186633010"));
            assertFalse(text.contains("KWAME NKRUMAH AVENUE"));
            assertFalse(text.contains("6101"));
            assertFalse(text.contains("COBZZWHAXXX"));
            assertTrue(text.contains("Dr Registrar"));
            assertTrue(text.contains("Yours sincerely"));
            assertTrue(text.contains("eMhare will generate your registration number"));
            assertTrue(text.contains("Quote that registration"));
            assertTrue(text.contains("number as the payment reference"));
            assertTrue(text.contains("I accept/do not accept the university offer"));
            assertFalse(text.contains("Full Name(s)"));
            assertFalse(text.contains("Signature................................"));
        }
    }

    @Test
    void rendersInternationalConditionalOfferWithoutInventingFeeAmounts() throws Exception {
        GeneratedDocument document=mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-OF-2028-000002");
        OfferLetterProjection offer=mock(OfferLetterProjection.class);
        when(offer.getOfferNumber()).thenReturn("OF-2028-000002");
        when(offer.getApplicationNumber()).thenReturn("EMH-2028-000002");
        when(offer.getApplicantName()).thenReturn("Tariro Dube");
        when(offer.getProgrammeName()).thenReturn("Master of Business Administration");
        when(offer.getProgrammeCode()).thenReturn("MBA");
        when(offer.getOfferType()).thenReturn("CONDITIONAL");
        when(offer.getConditionsText()).thenReturn("Submit the final degree transcript before registration.");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(offer.getContentSnapshot()).thenReturn(new OfferLetterContentSnapshot(
                "University of Zimbabwe", "University of Zimbabwe", null, null, null, null, null,
                "INTERNATIONAL", "MBA", "Master of Business Administration", "March 2028 Intake",
                "Faculty of Business Management Sciences and Economics", "Master of Business Administration",
                "Postgraduate", "2028.1", List.of(), List.of("Passport", "Original degree certificate"),
                new FeeScheduleSnapshot(UUID.randomUUID(), 1, "INTL-MBA", "USD", "USD", null, null,
                        List.of(), BigDecimal.ZERO, BigDecimal.ZERO),
                "Registrar", "Registrar", "UZ-OFFER-LETTER-2026-01"));

        var rendered=new OfficialOfferLetterPdfRenderer().render(document,offer);

        try(var pdf=Loader.loadPDF(rendered.content())){
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Submit the final degree transcript before registration."));
            assertTrue(text.contains("Finance has not supplied a published fee snapshot for this letter."));
            assertTrue(text.contains("Industrialisation Levy"));
            assertFalse(text.contains("USD 0.00"));
        }
    }

    @Test
    void rendersRatedForeignCurrencyEvidenceAndLegacySafeFallbacks() throws Exception {
        GeneratedDocument document = mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-OF-2028-000003");
        OfferLetterProjection offer = mock(OfferLetterProjection.class);
        when(offer.getOfferNumber()).thenReturn("OF-2028-000003");
        when(offer.getApplicationNumber()).thenReturn("EMH-2028-000003");
        when(offer.getApplicantName()).thenReturn("Rudo Ncube");
        when(offer.getApplicantEmail()).thenReturn("rudo@example.test");
        when(offer.getProgrammeName()).thenReturn("Bachelor of Laws");
        when(offer.getProgrammeCode()).thenReturn("LLB");
        when(offer.getOfferType()).thenReturn("FIRM");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        UUID rateId = UUID.randomUUID();
        when(offer.getContentSnapshot()).thenReturn(new OfferLetterContentSnapshot(
                " ", "University of Zimbabwe", " ", null, "admissions@uz.ac.zw", null, null,
                "FOREIGN", null, "Undergraduate", null, null, null, null, null,
                null, null,
                new FeeScheduleSnapshot(UUID.randomUUID(), 8, "UG-LAW-2028", "ZWG", "USD", rateId,
                        new BigDecimal("0.04"),
                        List.of(new FeeLineSnapshot("TUIT", " ", null, new BigDecimal("100.00"))),
                        null, new BigDecimal("100.00")),
                null, " ", null));

        var rendered = new OfficialOfferLetterPdfRenderer().render(document, offer);

        try (var pdf = Loader.loadPDF(rendered.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("USD base equivalent"));
            assertTrue(text.contains("exchange-rate evidence"));
            assertTrue(text.contains("TUIT"));
        }
    }

    @Test
    void rendersOlderProjectionWithConservativeDefaultsAndWrapsInvalidInputs() throws Exception {
        GeneratedDocument document = mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-LEGACY-1");
        OfferLetterProjection legacy = mock(OfferLetterProjection.class);
        when(legacy.getOfferNumber()).thenReturn("OF-LEGACY-1");
        when(legacy.getApplicationNumber()).thenReturn("APP-LEGACY-1");
        when(legacy.getApplicantName()).thenReturn("Legacy Applicant");
        when(legacy.getProgrammeName()).thenReturn("Legacy Programme");
        when(legacy.getProgrammeCode()).thenReturn("LEG");
        when(legacy.getOfferType()).thenReturn("FIRM");
        when(legacy.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));

        var rendered = new OfficialOfferLetterPdfRenderer().render(document, legacy);

        try (var pdf = Loader.loadPDF(rendered.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("TO THE Legacy Programme"));
            assertTrue(text.contains("Registration arrangements will be communicated"));
            assertTrue(text.contains("original identity and academic evidence"));
        }

        OfferLetterProjection invalid = mock(OfferLetterProjection.class);
        when(invalid.getOfferType()).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> new OfficialOfferLetterPdfRenderer().render(document, invalid));
    }

    @Test
    void rendersDepositInstructionAndUsesRequestYearWhenCommencementIsPending() throws Exception {
        GeneratedDocument document = mock(GeneratedDocument.class);
        when(document.getDocumentNumber()).thenReturn("OFFER-OF-2029-000004");
        when(document.getRequestedAt()).thenReturn(Instant.parse("2029-01-10T08:00:00Z"));
        OfferLetterProjection offer = mock(OfferLetterProjection.class);
        when(offer.getOfferNumber()).thenReturn("OF-2029-000004");
        when(offer.getApplicationNumber()).thenReturn("EMH-2029-000004");
        when(offer.getApplicantName()).thenReturn("Kudzai Zhou");
        when(offer.getProgrammeName()).thenReturn("Bachelor of Accountancy");
        when(offer.getProgrammeCode()).thenReturn("BACC");
        when(offer.getOfferType()).thenReturn("FIRM");
        when(offer.getConditionsText()).thenReturn("  ");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2029-02-28T21:59:59Z"));
        when(offer.getContentSnapshot()).thenReturn(new OfferLetterContentSnapshot(
                "University of Zimbabwe", "University of Zimbabwe", null, null, null, null, null,
                "LOCAL", "UNDERGRAD", "Undergraduate", "August 2029 Intake", " ",
                "Bachelor of Accountancy", "Undergraduate", "2029.1", List.of(), List.of("National identity document"),
                new FeeScheduleSnapshot(UUID.randomUUID(), 2, "UG-BACC-2029", "ZWG", "USD", null, null,
                        List.of(new FeeLineSnapshot(null, "Application charge", new BigDecimal("10.00"), null),
                                new FeeLineSnapshot("ACCEPTANCE_DEPOSIT", "Acceptance deposit", new BigDecimal("250.00"), null),
                                new FeeLineSnapshot("TUITION", "Tuition", new BigDecimal("4000.00"), null)),
                        new BigDecimal("4260.00"), null),
                "Registrar", "Registrar", "UZ-OFFER-LETTER-2026-01"));

        var rendered = new OfficialOfferLetterPdfRenderer().render(document, offer);

        try (var pdf = Loader.loadPDF(rendered.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("ADMISSION IN THE YEAR 2029"));
            assertTrue(text.contains("pay the non-refundable deposit of ZWG 250.00"));
            assertTrue(text.contains("Lecture commencement details will be communicated"));
            assertFalse(text.contains("Conditions of this offer:"));
            assertFalse(text.contains("USD base equivalent"));
        }
    }
}
