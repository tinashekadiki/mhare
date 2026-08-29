package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  @Test
  void pairsBorderlessQualificationSubjectsAndGradesUsingBoundingBoxRows() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "pages", Map.of("1", Map.of("size", Map.of("width", 2000, "height", 720))),
                "texts",
                    List.of(
                        textBlock("UZUMBA SECONDARY SCHOOL", 260, 520, 930, 550),
                        textBlock("ENGLISH_LANGUAGE", 280, 410, 730, 440),
                        textBlock("RELIGIOUS STUDIES", 280, 370, 760, 400),
                        textBlock("HISTORY", 280, 330, 540, 360),
                        textBlock("GEOGRAPHY", 280, 290, 610, 320),
                        textBlock("SHONA", 280, 250, 500, 280),
                        textBlock("C (c)", 1600, 410, 1740, 440),
                        textBlock("A B C", 1600, 290, 1740, 400),
                        textBlock("D (d)", 1600, 250, 1740, 280))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            "Zimbabwe School Examinations Council\nUZUMBA SECONDARY SCHOOL\n080120/3019\nExamination of November 2001",
            structuredExtraction,
            "O_LEVEL");

    assertEquals("ZIMSEC", extraction.facts().get("examBodyCode"));
    assertEquals("ZWE", extraction.facts().get("countryCode"));
    assertEquals(2001, extraction.facts().get("yearWritten"));
    assertEquals("UZUMBA SECONDARY SCHOOL", extraction.facts().get("schoolOrInstitution"));
    assertEquals("080120", extraction.facts().get("centreNumber"));
    assertEquals("3019", extraction.facts().get("candidateNumber"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(5, results.size());
    assertEquals(Map.of("subjectName", "ENGLISH LANGUAGE", "grade", "C"), results.get(0));
    assertEquals(Map.of("subjectName", "RELIGIOUS STUDIES", "grade", "A"), results.get(1));
    assertEquals(Map.of("subjectName", "HISTORY", "grade", "B"), results.get(2));
    assertEquals(Map.of("subjectName", "GEOGRAPHY", "grade", "C"), results.get(3));
    assertEquals(Map.of("subjectName", "SHONA", "grade", "D"), results.get(4));
    assertTrue(extraction.warnings().stream().noneMatch(value -> value.contains("identity facts")));
  }

  @Test
  void normalisesDotMatrixGradeCharactersFromOneGroupedBoundingBox() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "pages", Map.of("1", Map.of("size", Map.of("width", 2400, "height", 900))),
                "texts",
                    List.of(
                        textBlock("ENGLISH LANGUAGE", 350, 398, 900, 430),
                        textBlock("RELIGIOUS STUDIES", 350, 364, 930, 399),
                        textBlock("HISTORY", 350, 335, 600, 364),
                        textBlock("GEOGRAPHY", 350, 302, 660, 334),
                        textBlock("SHONA", 350, 268, 530, 301),
                        textBlock("INTEGRATED SCIENCE", 350, 235, 965, 270),
                        textBlock("COMMERCE", 350, 205, 625, 238),
                        textBlock("C <⊂) CRUA (a) (b) (⊂) (P) (⊂) 0 C", 1890, 206, 2075, 433))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Ordinary Level", structuredExtraction, "O_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(
        List.of(
            Map.of("subjectName", "ENGLISH LANGUAGE", "grade", "C"),
            Map.of("subjectName", "RELIGIOUS STUDIES", "grade", "A"),
            Map.of("subjectName", "HISTORY", "grade", "B"),
            Map.of("subjectName", "GEOGRAPHY", "grade", "C"),
            Map.of("subjectName", "SHONA", "grade", "D"),
            Map.of("subjectName", "INTEGRATED SCIENCE", "grade", "C"),
            Map.of("subjectName", "COMMERCE", "grade", "C")),
        results);
  }

  @Test
  void keepsDetectedSubjectsWhenAQualificationGradeCannotBeReadReliably() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "pages", Map.of("1", Map.of("size", Map.of("width", 2000, "height", 720))),
                "texts",
                    List.of(
                        textBlock("INTEGRATED SCIENCE", 280, 220, 780, 250),
                        textBlock("COMMERCE", 280, 180, 550, 210),
                        textBlock("C (c)", 1600, 180, 1740, 210))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Ordinary Level", structuredExtraction, "O_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(Map.of("subjectName", "INTEGRATED SCIENCE", "grade", ""), results.getFirst());
    assertEquals(Map.of("subjectName", "COMMERCE", "grade", "C"), results.get(1));
    assertTrue(extraction.warnings().stream().anyMatch(value -> value.contains("grade")));
  }

  @Test
  void readsLayoutAwareTableCellsWhenDoclingRecognisesAResultsTable() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts", List.of(),
                "tables",
                    List.of(
                        Map.of(
                            "data",
                            Map.of(
                                "table_cells",
                                List.of(
                                    tableCell("Syllabus Title", 0, 1, true, false),
                                    tableCell("Result A (a)", 0, 3, true, false),
                                    tableCell("6030 BIOLOGY", 1, 1, false, true),
                                    tableCell("Advanced level", 1, 2, false, false),
                                    tableCell("6031 CHEMISTRY", 2, 1, false, true),
                                    tableCell("Advanced level", 2, 2, false, false),
                                    tableCell("C (c)", 2, 3, false, false),
                                    tableCell("6042 PURE MATHEMATICS", 3, 1, false, true),
                                    tableCell("Advanced level", 3, 2, false, false),
                                    tableCell("A (a)", 3, 3, false, false),
                                    tableCell("5033 COMMUNICATION SKILLS", 4, 1, false, true),
                                    tableCell("Pass", 4, 2, false, false),
                                    tableCell("3", 4, 3, false, false)))))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            "ZIMSEC Statement of Results November 2024", structuredExtraction, "A_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(3, results.size());
    assertEquals(Map.of("subjectName", "BIOLOGY", "grade", ""), results.get(0));
    assertEquals(Map.of("subjectName", "CHEMISTRY", "grade", "C"), results.get(1));
    assertEquals(Map.of("subjectName", "PURE MATHEMATICS", "grade", "A"), results.get(2));
  }

  @Test
  void readsTheResultColumnImmediatelyAfterTheSubjectColumn() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(),
                "tables",
                List.of(
                    Map.of(
                        "data",
                        Map.of(
                            "table_cells",
                            List.of(
                                tableCell("Subject", 0, 0, true, false),
                                tableCell("Result", 0, 1, true, false),
                                tableCell("MATHEMATICS", 1, 0, false, true),
                                tableCell("A", 1, 1, false, false),
                                tableCell("ENGLISH LANGUAGE", 2, 0, false, true),
                                tableCell("C", 2, 1, false, false)))))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Provisional Results", structuredExtraction, "O_LEVEL");

    assertEquals(
        List.of(
            Map.of("subjectName", "MATHEMATICS", "grade", "A"),
            Map.of("subjectName", "ENGLISH LANGUAGE", "grade", "C")),
        extraction.facts().get("qualificationResults"));
  }

  @Test
  void selectsTheMostCompleteTableAcrossPrimaryAndQualificationRegionDocuments() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "document",
            resultsTableDocument("MATHEMATICS", "A", "ENGLISH LANGUAGE", "C", "HISTORY", "B"),
            "qualificationRegionDocument",
            resultsTableDocument("MATHEMATICS", "A"));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Ordinary Level", structuredExtraction, "O_LEVEL");

    assertEquals(3, ((List<?>) extraction.facts().get("qualificationResults")).size());
  }

  @Test
  void selectsCoordinateRowsWhenDoclingReturnsOnlyAPartialLayoutTable() {
    Map<String, Object> regionDocument = new LinkedHashMap<>();
    regionDocument.putAll(resultsTableDocument("MATHEMATICS", "A"));
    regionDocument.put(
        "texts",
        List.of(
            textBlock("MATHEMATICS", 200, 200, 700, 230),
            textBlock("ENGLISH LANGUAGE", 200, 160, 800, 190),
            textBlock("A", 1600, 200, 1640, 230),
            textBlock("C", 1600, 160, 1640, 190)));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            "ZIMSEC Ordinary Level",
            Map.of("qualificationRegionDocument", regionDocument),
            "O_LEVEL");

    assertEquals(2, ((List<?>) extraction.facts().get("qualificationResults")).size());
  }

  @Test
  void prefersCompleteImplicitTableRowsOverMoreUnpairedCoordinateNoise() {
    List<Map<String, Object>> noisyLayout = new java.util.ArrayList<>();
    for (int index = 0; index < 12; index++) {
      noisyLayout.add(
          textBlock("NOISY SUBJECT " + index, 200, 600 - index * 30, 800, 620 - index * 30));
    }
    noisyLayout.add(textBlock("A B C", 1600, 500, 1700, 620));
    Map<String, Object> document = new LinkedHashMap<>();
    document.putAll(
        resultsTableDocument("BIOLOGY", "A", "CHEMISTRY", "C", "PURE MATHEMATICS", "A"));
    document.put("texts", noisyLayout);

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(
            "ZIMSEC Advanced Level", Map.of("qualificationRegionDocument", document), "A_LEVEL");

    assertEquals(
        List.of(
            Map.of("subjectName", "BIOLOGY", "grade", "A"),
            Map.of("subjectName", "CHEMISTRY", "grade", "C"),
            Map.of("subjectName", "PURE MATHEMATICS", "grade", "A")),
        extraction.facts().get("qualificationResults"));
  }

  @Test
  void pairsSubjectsAndGradesWhenDoclingReturnsMultilineColumnBlocks() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(
                    textBlock(
                        "ENGLISH LANGUAGE\nNDEBELE\nINTEGRATED SCIENCE\nAGRICULTURE",
                        200,
                        100,
                        900,
                        300),
                    textBlock("E (e)\nC (c)\nE (e)\nC (c)", 1600, 100, 1700, 300))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Ordinary Level", structuredExtraction, "O_LEVEL");

    assertEquals(
        List.of(
            Map.of("subjectName", "ENGLISH LANGUAGE", "grade", "E"),
            Map.of("subjectName", "NDEBELE", "grade", "C"),
            Map.of("subjectName", "INTEGRATED SCIENCE", "grade", "E"),
            Map.of("subjectName", "AGRICULTURE", "grade", "C")),
        extraction.facts().get("qualificationResults"));
  }

  @Test
  void ignoresMalformedDoclingLayoutEntriesWithoutDiscardingManualQualificationCapture() {
    Map<String, Object> missingCoordinateBoundingBox = new LinkedHashMap<>();
    missingCoordinateBoundingBox.put("l", 10);
    missingCoordinateBoundingBox.put("b", 20);
    missingCoordinateBoundingBox.put("r", 30);
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionText",
            "Ordinary Level",
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(
                    "not-a-text-entry",
                    Map.of(),
                    Map.of("text", "ENGLISH LANGUAGE", "prov", "not-provenance"),
                    Map.of("text", "ENGLISH LANGUAGE", "prov", List.of()),
                    Map.of("text", "ENGLISH LANGUAGE", "prov", List.of("not-a-provenance-entry")),
                    Map.of(
                        "text",
                        "ENGLISH LANGUAGE",
                        "prov",
                        List.of(Map.of("bbox", "not-a-bounding-box"))),
                    Map.of(
                        "text",
                        "ENGLISH LANGUAGE",
                        "prov",
                        List.of(Map.of("bbox", missingCoordinateBoundingBox))))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract(null, structuredExtraction, " academic_qualification ");

    assertNull(extraction.facts().get("qualificationResults"));
    assertTrue(extraction.warnings().getFirst().contains("manually"));
  }

  @Test
  void skipsMalformedTableCellsAndUsesImplicitTableColumnsWithoutRowHeaders() {
    Map<String, Object> cellWithoutText = new LinkedHashMap<>();
    cellWithoutText.put("start_row_offset_idx", 7);
    cellWithoutText.put("start_col_offset_idx", 1);
    cellWithoutText.put("column_header", false);
    cellWithoutText.put("row_header", false);
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(),
                "tables",
                List.of(
                    "not-a-table",
                    Map.of("data", "not-table-data"),
                    Map.of("data", Map.of("table_cells", "not-table-cells")),
                    Map.of(
                        "data",
                        Map.of(
                            "table_cells",
                            List.of(
                                "not-a-cell",
                                tableCell("Subject", 0, 1, true, false),
                                Map.of(
                                    "text",
                                    "BAD ROW",
                                    "start_row_offset_idx",
                                    "one",
                                    "start_col_offset_idx",
                                    1,
                                    "column_header",
                                    false,
                                    "row_header",
                                    false),
                                Map.of(
                                    "text",
                                    "BAD COLUMN",
                                    "start_row_offset_idx",
                                    7,
                                    "start_col_offset_idx",
                                    "one",
                                    "column_header",
                                    false,
                                    "row_header",
                                    false),
                                cellWithoutText,
                                tableCell("ENGLISH LANGUAGE", 1, 1, false, false),
                                tableCell("Ordinary", 1, 2, false, false),
                                tableCell("C (c)", 1, 3, false, false),
                                tableCell("12", 2, 1, false, true),
                                tableCell("HISTORY", 3, 1, false, false)))))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC", structuredExtraction, "O_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(List.of(Map.of("subjectName", "ENGLISH LANGUAGE", "grade", "C")), results);
  }

  @Test
  void pairsOneNearbyGradeAndRejectsNonSubjectLayoutNoise() {
    String overlongHeading = "X".repeat(81);
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "tables",
                List.of(Map.of("data", Map.of("table_cells", List.of()))),
                "texts",
                List.of(
                    textBlock("A", 100, 300, 120, 320),
                    textBlock(overlongHeading, 100, 270, 900, 290),
                    textBlock("123", 100, 240, 160, 260),
                    textBlock("CANDIDATE NUMBER", 100, 210, 500, 230),
                    textBlock("C (c)", 100, 180, 180, 200),
                    textBlock("HISTORY", 200, 140, 600, 160),
                    textBlock("B", 1600, 100, 1640, 130))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("Zimbabwe School Examinations Council", structuredExtraction, "O_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals(List.of(Map.of("subjectName", "HISTORY", "grade", "B")), results);
  }

  @Test
  void leavesGroupedGradesBlankWhenRowsCannotBeAlignedSafely() {
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(
                    textBlock("MATHEMATICS", 200, 200, 650, 230),
                    textBlock("ENGLISH LANGUAGE", 200, 160, 750, 190),
                    textBlock("A B C", 1600, 150, 1680, 240))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC Ordinary Level", structuredExtraction, "O_LEVEL");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results =
        (List<Map<String, Object>>) extraction.facts().get("qualificationResults");
    assertEquals("", results.getFirst().get("grade"));
    assertEquals("", results.get(1).get("grade"));
    assertTrue(extraction.warnings().stream().anyMatch(value -> value.contains("aligned safely")));
  }

  @Test
  void reportsNoRowsWhenOnlyOneSideOfAnImplicitTableIsDetected() {
    ApplicantEvidenceFactExtractor.ExtractionFacts subjectsOnly =
        extractor.extract(
            "ZIMSEC Ordinary Level",
            Map.of(
                "qualificationRegionDocument",
                Map.of("texts", List.of(textBlock("MATHEMATICS", 100, 100, 500, 130)))),
            "O_LEVEL");
    ApplicantEvidenceFactExtractor.ExtractionFacts gradesOnly =
        extractor.extract(
            "ZIMSEC Ordinary Level",
            Map.of(
                "qualificationRegionDocument",
                Map.of("texts", List.of(textBlock("A", 1600, 100, 1640, 130)))),
            "O_LEVEL");

    assertNull(subjectsOnly.facts().get("qualificationResults"));
    assertNull(gradesOnly.facts().get("qualificationResults"));
  }

  @Test
  void limitsLayoutAwareTableResultsToTwentyRows() {
    List<Map<String, Object>> cells = new java.util.ArrayList<>();
    cells.add(tableCell("Syllabus Title", 0, 1, true, false));
    for (int row = 1; row <= 21; row++) {
      cells.add(tableCell("SUBJECT NAME " + row, row, 1, false, true));
      cells.add(tableCell("Ordinary", row, 2, false, false));
      cells.add(tableCell("A", row, 3, false, false));
    }
    Map<String, Object> structuredExtraction =
        Map.of(
            "qualificationRegionDocument",
            Map.of(
                "texts",
                List.of(),
                "tables",
                List.of(Map.of("data", Map.of("table_cells", cells)))));

    ApplicantEvidenceFactExtractor.ExtractionFacts extraction =
        extractor.extract("ZIMSEC", structuredExtraction, "O_LEVEL");

    assertEquals(20, ((List<?>) extraction.facts().get("qualificationResults")).size());
  }

  private Map<String, Object> textBlock(
      String text, double left, double bottom, double right, double top) {
    return Map.of(
        "text",
        text,
        "prov",
        List.of(
            Map.of(
                "bbox",
                Map.of(
                    "l", left,
                    "b", bottom,
                    "r", right,
                    "t", top,
                    "coord_origin", "BOTTOMLEFT"))));
  }

  private Map<String, Object> tableCell(
      String text, int row, int column, boolean columnHeader, boolean rowHeader) {
    return Map.of(
        "text",
        text,
        "start_row_offset_idx",
        row,
        "start_col_offset_idx",
        column,
        "column_header",
        columnHeader,
        "row_header",
        rowHeader);
  }

  private Map<String, Object> resultsTableDocument(String... subjectGradePairs) {
    List<Map<String, Object>> cells = new java.util.ArrayList<>();
    cells.add(tableCell("Subject", 0, 0, true, false));
    cells.add(tableCell("Result", 0, 1, true, false));
    for (int index = 0; index < subjectGradePairs.length; index += 2) {
      int row = index / 2 + 1;
      cells.add(tableCell(subjectGradePairs[index], row, 0, false, true));
      cells.add(tableCell(subjectGradePairs[index + 1], row, 1, false, false));
    }
    return Map.of(
        "texts", List.of(), "tables", List.of(Map.of("data", Map.of("table_cells", cells))));
  }
}
