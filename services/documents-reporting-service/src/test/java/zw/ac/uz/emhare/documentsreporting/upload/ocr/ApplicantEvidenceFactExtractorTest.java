package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @author Tinashe K
 */
class ApplicantEvidenceFactExtractorTest {

  private final ApplicantEvidenceFactExtractor extractor = new ApplicantEvidenceFactExtractor();

  @Test
  void proposesIdentityFieldsWithoutTreatingThemAsPersistedAdmissionsData() {
    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            """
            National ID: 63-123456A18
            Date of Birth: 15/04/2004
            Gender: Female
            Place of Birth: Harare
            First Name: Tariro
            Surname: Moyo
            """);

    assertEquals("63-123456A18", extraction.facts().get("nationalIdNumber"));
    assertEquals("15/04/2004", extraction.facts().get("dateOfBirth"));
    assertEquals("Female", extraction.facts().get("genderCode"));
    assertEquals("Tariro", extraction.facts().get("firstName"));
    assertEquals("Moyo", extraction.facts().get("lastName"));
    assertTrue(extraction.warnings().getFirst().contains("proposals"));
  }

  @Test
  void keepsManualEntryAvailableWhenNoFactsAreRecognised() {
    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("blurred scan with no recognised labels");

    assertEquals(1, extraction.facts().size());
    assertTrue(extraction.warnings().getFirst().contains("manually"));
  }

  @Test
  void rejectsPassportMachineReadableZoneWhenCheckDigitsDoNotValidate() {
    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            """
            P<ZWEERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C37ZWE7408122F1204159ZE184226B<<<<<10
            """);

    assertNull(extraction.facts().get("passportNumber"));
    assertTrue(extraction.warnings().stream().anyMatch(value -> value.contains("check digits")));
  }

  @Test
  void identifiesZimbabweNationalCardWhenOcrSeparatesTheCountryAndRegistrationHeadings() {
    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            """
            REPUBLIC OF ZIMBABWE
            ID Number
            NATIONAL REGISTRATION
            Surname
            First Name
            """);

    assertEquals("ZIMBABWE_NATIONAL_ID", extraction.facts().get("documentType"));
    assertEquals("ZWE", extraction.facts().get("nationalityCode"));
  }
}
