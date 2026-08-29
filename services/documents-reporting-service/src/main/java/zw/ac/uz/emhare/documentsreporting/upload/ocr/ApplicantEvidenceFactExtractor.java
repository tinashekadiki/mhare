package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Conservative generic fact proposals; Admissions performs managed-reference matching. @author
 * Tinashe K
 */
@Component
public class ApplicantEvidenceFactExtractor {

  private static final Pattern ZIMBABWE_REPUBLIC =
      Pattern.compile("(?i)REPUBLIC\\s+OF\\s+ZIMBABWE");
  private static final Pattern NATIONAL_REGISTRATION_HEADING =
      Pattern.compile("(?i)NATIONAL\\s+REGISTRATION");
  private static final Pattern ZIMBABWE_NATIONAL_ID =
      Pattern.compile("(?i)\\b(\\d{2})\\s*[-–—]?\\s*(\\d{6,7})\\s*([A-Z])\\s*(\\d{2})\\b");
  private static final Pattern ZIMBABWE_CITIZEN_GENDER = Pattern.compile("(?i)\\bCIT\\s*([MF])\\b");

  private static final Pattern NATIONAL_ID =
      Pattern.compile(
          "(?i)\\b(?:national\\s*(?:id|identity)(?:\\s*(?:no|number))?\\s*[:#-]?\\s*)?(\\d{2}[- ]?\\d{6,7}[A-Z]\\d{2})\\b");
  private static final Pattern PASSPORT =
      Pattern.compile("(?i)\\bpassport(?:\\s*(?:no|number))?\\s*[:#-]?\\s*([A-Z0-9]{6,12})\\b");
  private static final Pattern DATE_OF_BIRTH =
      Pattern.compile(
          "(?i)(?:date\\s+of\\s+birth|birth\\s+date|dob)\\s*[:#-]?\\s*(\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{4}|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2})");
  private static final Pattern GENDER =
      Pattern.compile("(?i)(?:sex|gender)\\s*[:#-]?\\s*(male|female|m|f)\\b");
  private static final Pattern NATIONALITY =
      Pattern.compile("(?i)nationality\\s*[:#-]?\\s*([A-Z][A-Za-z ]{2,40})");
  private static final Pattern PLACE_OF_BIRTH =
      Pattern.compile(
          "(?i)(?:place\\s+of\\s+birth|birthplace)\\s*[:#-]?\\s*([A-Z][A-Za-z ,'-]{2,80})");
  private static final Pattern FIRST_NAME =
      Pattern.compile("(?i)(?:first|given)\\s+name(?:s)?\\s*[:#-]?\\s*([A-Z][A-Za-z '-]{1,80})");
  private static final Pattern LAST_NAME =
      Pattern.compile("(?i)(?:surname|last\\s+name)\\s*[:#-]?\\s*([A-Z][A-Za-z '-]{1,80})");
  private static final Pattern MIDDLE_NAMES =
      Pattern.compile("(?i)middle\\s+name(?:s)?\\s*[:#-]?\\s*([A-Z][A-Za-z '-]{1,100})");
  private static final Pattern QUALIFICATION_YEAR =
      Pattern.compile(
          "(?i)(?:EXAMINATION\\s+OF\\s+|SESSION\\s+)?(?:JANUARY|JUNE|NOVEMBER)?\\s*(19\\d{2}|20\\d{2})");
  private static final Pattern ISOLATED_GRADE =
      Pattern.compile("(?i)(?<![A-Z])([A-EU])(?![A-Z])(?:\\s*\\([^)]*\\))?");
  private static final Pattern COMPACT_GRADES = Pattern.compile("(?i)^[A-EU]{2,20}$");
  private static final Pattern OCR_PARENTHETICAL_GRADE =
      Pattern.compile("(?i)(?:\\(|<)\\s*([A-EU⊂P])\\s*\\)");
  private static final Pattern ZIMSEC_CANDIDATE_NUMBER =
      Pattern.compile("(?<!\\d)(\\d{4,6})\\s*/\\s*(\\d{3,4})(?!\\d)");
  private static final List<String> QUALIFICATION_DOCUMENT_KEYS =
      List.of("document", "qualificationRegionDocument", "qualificationContrastRegionDocument");
  private static final List<String> QUALIFICATION_TEXT_KEYS =
      List.of("qualificationRegionText", "qualificationContrastRegionText");
  private static final List<String> NON_SUBJECT_MARKERS =
      List.of(
          "SCHOOL",
          "COLLEGE",
          "EXAMINATION",
          "NUMBER OF",
          "SUBJECTS",
          "ZIMBABWE",
          "ZIMSEC",
          "CERTIFICATE",
          "ORDINARY LEVEL",
          "ADVANCED LEVEL",
          "CANDIDATE",
          "CENTRE",
          "RESULT",
          "STATUS",
          "EXPLANATION",
          "QUALIFICATION",
          "SYLLABUS",
          "SESSION",
          "DATE OF BIRTH");

  public ExtractionFacts extract(String text) {
    return extract(text, Map.of(), null);
  }

  public ExtractionFacts extract(
      String text, Map<String, Object> structuredExtraction, String documentTypeCode) {
    String source = text == null ? "" : text;
    Map<String, Object> facts = new LinkedHashMap<>();
    Map<String, Double> confidence = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();
    extractZimbabweNationalIdentityCard(source, facts, confidence);
    extractPassportMachineReadableZone(source, facts, confidence, warnings);
    propose(facts, confidence, "nationalIdNumber", NATIONAL_ID, source, 0.92);
    propose(facts, confidence, "passportNumber", PASSPORT, source, 0.90);
    propose(facts, confidence, "dateOfBirth", DATE_OF_BIRTH, source, 0.84);
    propose(facts, confidence, "genderCode", GENDER, source, 0.82);
    propose(facts, confidence, "nationality", NATIONALITY, source, 0.72);
    propose(facts, confidence, "placeOfBirth", PLACE_OF_BIRTH, source, 0.70);
    propose(facts, confidence, "firstName", FIRST_NAME, source, 0.76);
    propose(facts, confidence, "lastName", LAST_NAME, source, 0.78);
    propose(facts, confidence, "middleNames", MIDDLE_NAMES, source, 0.74);
    boolean qualificationEvidence =
        isQualificationEvidence(source, structuredExtraction, documentTypeCode);
    if (qualificationEvidence) {
      extractQualificationFacts(source, structuredExtraction, facts, confidence, warnings);
    }
    facts.put(
        "lines",
        source.lines().map(String::trim).filter(line -> !line.isBlank()).limit(300).toList());
    if (qualificationEvidence) {
      if (!facts.containsKey("qualificationResults")) {
        warnings.add(
            "No qualification subjects could be read reliably; complete the results manually.");
      } else if (hasMissingQualificationGrade(facts)) {
        warnings.add(
            "One or more qualification grades could not be read reliably; confirm them against the uploaded evidence.");
      } else {
        warnings.add(
            "Qualification subjects and grades are OCR proposals and must be checked against the uploaded evidence.");
      }
    } else {
      warnings.add(
          facts.size() <= 1
              ? "No high-confidence identity facts were detected; complete the form manually."
              : "OCR values are proposals and must be checked against the uploaded evidence.");
    }
    return new ExtractionFacts(facts, confidence, List.copyOf(warnings));
  }

  private boolean isQualificationEvidence(
      String source, Map<String, Object> structuredExtraction, String documentTypeCode) {
    String normalizedCode =
        documentTypeCode == null ? "" : documentTypeCode.trim().toUpperCase(Locale.ROOT);
    if (normalizedCode.equals("O_LEVEL")
        || normalizedCode.equals("A_LEVEL")
        || normalizedCode.contains("QUALIFICATION")) return true;
    String searchable =
        qualificationSearchableText(source, structuredExtraction).toUpperCase(Locale.ROOT);
    return searchable.contains("ZIMSEC")
        || searchable.contains("ZIMBABWE SCHOOL EXAMINATIONS COUNCIL")
        || searchable.contains("ORDINARY LEVEL")
        || searchable.contains("ADVANCED LEVEL");
  }

  private void extractQualificationFacts(
      String source,
      Map<String, Object> structuredExtraction,
      Map<String, Object> facts,
      Map<String, Double> confidence,
      List<String> warnings) {
    String searchable = qualificationSearchableText(source, structuredExtraction);
    if (searchable.toUpperCase(Locale.ROOT).contains("ZIMSEC")
        || searchable.toUpperCase(Locale.ROOT).contains("ZIMBABWE SCHOOL EXAMINATIONS COUNCIL")) {
      facts.put("examBodyCode", "ZIMSEC");
      facts.put("countryCode", "ZWE");
      confidence.put("examBodyCode", 0.98);
      confidence.put("countryCode", 0.98);
    }
    Matcher yearMatcher = QUALIFICATION_YEAR.matcher(searchable);
    Integer lastYear = null;
    while (yearMatcher.find()) lastYear = Integer.valueOf(yearMatcher.group(1));
    if (lastYear != null) {
      facts.put("yearWritten", lastYear);
      confidence.put("yearWritten", 0.88);
    }
    Matcher candidateNumberMatcher = ZIMSEC_CANDIDATE_NUMBER.matcher(searchable);
    if (candidateNumberMatcher.find()) {
      facts.put("centreNumber", candidateNumberMatcher.group(1));
      facts.put("candidateNumber", candidateNumberMatcher.group(2));
      confidence.put("centreNumber", 0.84);
      confidence.put("candidateNumber", 0.84);
    }

    List<LayoutText> blocks = qualificationLayoutBlocksAcrossDocuments(structuredExtraction);
    java.util.Optional<String> schoolOrInstitution =
        searchable
            .lines()
            .map(this::normalizeQualificationText)
            .filter(value -> value.endsWith(" SCHOOL") || value.endsWith(" COLLEGE"))
            .findFirst()
            .or(
                () ->
                    blocks.stream()
                        .map(LayoutText::text)
                        .map(this::normalizeQualificationText)
                        .filter(value -> value.endsWith(" SCHOOL") || value.endsWith(" COLLEGE"))
                        .findFirst());
    schoolOrInstitution.ifPresent(
        school -> {
          facts.put("schoolOrInstitution", school);
          confidence.put("schoolOrInstitution", 0.82);
        });
    List<Map<String, Object>> results = bestQualificationResults(structuredExtraction, warnings);
    if (!results.isEmpty()) {
      facts.put("qualificationResults", results);
      confidence.put("qualificationResults", 0.78);
    }
  }

  private List<LayoutText> qualificationLayoutBlocksAcrossDocuments(
      Map<String, Object> structuredExtraction) {
    List<LayoutText> blocks = new ArrayList<>();
    for (String documentKey : QUALIFICATION_DOCUMENT_KEYS) {
      Object documentValue = structuredExtraction.get(documentKey);
      if (documentValue instanceof Map<?, ?> document) {
        blocks.addAll(qualificationLayoutBlocks(document));
      }
    }
    return List.copyOf(blocks);
  }

  private List<LayoutText> qualificationLayoutBlocks(Map<?, ?> document) {
    Object textsValue = document.get("texts");
    if (!(textsValue instanceof List<?> texts)) return List.of();
    List<LayoutText> blocks = new ArrayList<>();
    for (Object textValue : texts) {
      if (!(textValue instanceof Map<?, ?> textEntry)) continue;
      Object rawText = textEntry.get("text");
      Object provenanceValue = textEntry.get("prov");
      if (rawText == null
          || !(provenanceValue instanceof List<?> provenance)
          || provenance.isEmpty()) {
        continue;
      }
      Object firstProvenance = provenance.getFirst();
      if (!(firstProvenance instanceof Map<?, ?> provenanceEntry)) continue;
      Object boundingBoxValue = provenanceEntry.get("bbox");
      if (!(boundingBoxValue instanceof Map<?, ?> boundingBox)) continue;
      Double left = number(boundingBox.get("l"));
      Double bottom = number(boundingBox.get("b"));
      Double right = number(boundingBox.get("r"));
      Double top = number(boundingBox.get("t"));
      if (left == null || bottom == null || right == null || top == null) continue;
      blocks.addAll(splitMultilineLayoutText(String.valueOf(rawText), left, bottom, right, top));
    }
    return blocks;
  }

  private List<LayoutText> splitMultilineLayoutText(
      String rawText, double left, double bottom, double right, double top) {
    List<String> lines =
        rawText.lines().map(String::trim).filter(line -> !line.isBlank()).limit(20).toList();
    if (lines.size() <= 1) return List.of(new LayoutText(rawText, left, bottom, right, top));
    double verticalTop = Math.max(top, bottom);
    double verticalBottom = Math.min(top, bottom);
    double rowHeight = (verticalTop - verticalBottom) / lines.size();
    List<LayoutText> rows = new ArrayList<>();
    for (int index = 0; index < lines.size(); index++) {
      double rowTop = verticalTop - rowHeight * index;
      double rowBottom = verticalTop - rowHeight * (index + 1);
      rows.add(new LayoutText(lines.get(index), left, rowBottom, right, rowTop));
    }
    return List.copyOf(rows);
  }

  private List<Map<String, Object>> bestQualificationResults(
      Map<String, Object> structuredExtraction, List<String> warnings) {
    List<QualificationResultCandidate> candidates = new ArrayList<>();
    for (String documentKey : QUALIFICATION_DOCUMENT_KEYS) {
      Object documentValue = structuredExtraction.get(documentKey);
      if (!(documentValue instanceof Map<?, ?> document)) continue;
      List<Map<String, Object>> tableResults = qualificationTableResults(document);
      if (!tableResults.isEmpty()) {
        candidates.add(new QualificationResultCandidate(tableResults, List.of()));
      }
      List<String> coordinateWarnings = new ArrayList<>();
      List<Map<String, Object>> coordinateResults =
          pairQualificationRows(qualificationLayoutBlocks(document), coordinateWarnings);
      if (!coordinateResults.isEmpty()) {
        candidates.add(
            new QualificationResultCandidate(coordinateResults, List.copyOf(coordinateWarnings)));
      }
    }
    QualificationResultCandidate selected =
        candidates.stream()
            .max(
                Comparator.comparingInt(
                        (QualificationResultCandidate candidate) ->
                            qualificationResultQuality(candidate.results()))
                    .thenComparingInt(candidate -> candidate.results().size()))
            .orElse(null);
    if (selected == null) return List.of();
    warnings.addAll(selected.warnings());
    return selected.results();
  }

  private int qualificationResultQuality(List<Map<String, Object>> results) {
    int completedRows =
        (int)
            results.stream()
                .filter(result -> !String.valueOf(result.getOrDefault("grade", "")).isBlank())
                .count();
    return results.size() + completedRows * 100;
  }

  private String qualificationSearchableText(
      String source, Map<String, Object> structuredExtraction) {
    StringBuilder searchable = new StringBuilder(source == null ? "" : source);
    for (String textKey : QUALIFICATION_TEXT_KEYS) {
      Object value = structuredExtraction.get(textKey);
      if (value != null && !String.valueOf(value).isBlank()) {
        searchable.append('\n').append(value);
      }
    }
    return searchable.toString();
  }

  private List<Map<String, Object>> qualificationTableResults(Map<?, ?> document) {
    Object tablesValue = document.get("tables");
    if (!(tablesValue instanceof List<?> tables)) return List.of();
    List<Map<String, Object>> bestResults = List.of();
    for (Object tableValue : tables) {
      if (!(tableValue instanceof Map<?, ?> table)) continue;
      Object dataValue = table.get("data");
      if (!(dataValue instanceof Map<?, ?> data)) continue;
      Object cellsValue = data.get("table_cells");
      if (!(cellsValue instanceof List<?> cells)) continue;
      Map<Integer, List<TableCell>> cellsByRow = new java.util.TreeMap<>();
      for (Object cellValue : cells) {
        if (!(cellValue instanceof Map<?, ?> cell)) continue;
        Integer row = integer(cell.get("start_row_offset_idx"));
        Integer column = integer(cell.get("start_col_offset_idx"));
        Object rawText = cell.get("text");
        if (row == null
            || column == null
            || rawText == null
            || booleanValue(cell.get("column_header"))) continue;
        cellsByRow
            .computeIfAbsent(row, ignored -> new ArrayList<>())
            .add(
                new TableCell(
                    String.valueOf(rawText), column, booleanValue(cell.get("row_header"))));
      }
      List<Map<String, Object>> results = new ArrayList<>();
      for (List<TableCell> row : cellsByRow.values()) {
        TableCell subjectCell =
            row.stream()
                .filter(TableCell::rowHeader)
                .findFirst()
                .orElseGet(
                    () ->
                        row.stream()
                            .filter(cell -> cell.column() <= 1 && isSubjectCandidate(cell.text()))
                            .findFirst()
                            .orElse(null));
        if (subjectCell == null) continue;
        String subject =
            normalizeQualificationText(subjectCell.text()).replaceFirst("^\\d{3,5}\\s+", "");
        if (!isSubjectCandidate(subject)) continue;
        boolean schoolQualificationRow =
            row.stream()
                .map(TableCell::text)
                .map(this::normalizeQualificationText)
                .anyMatch(value -> value.contains("ADVANCED LEVEL") || value.contains("ORDINARY"));
        int subjectColumn = subjectCell.column();
        String grade =
            row.stream()
                .filter(cell -> cell.column() > subjectColumn)
                .map(TableCell::text)
                .map(this::extractGradeTokens)
                .filter(tokens -> !tokens.isEmpty())
                .map(List::getFirst)
                .findFirst()
                .orElse("");
        if (!schoolQualificationRow && grade.isBlank()) continue;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectName", subject);
        result.put("grade", grade);
        results.add(result);
        if (results.size() == 20) break;
      }
      if (results.size() > bestResults.size()
          || (results.size() == bestResults.size()
              && qualificationResultQuality(results) > qualificationResultQuality(bestResults))) {
        bestResults = List.copyOf(results);
      }
    }
    return bestResults;
  }

  private List<Map<String, Object>> pairQualificationRows(
      List<LayoutText> blocks, List<String> warnings) {
    if (blocks.isEmpty()) return List.of();
    double pageWidth = blocks.stream().mapToDouble(LayoutText::right).max().orElse(1.0);
    List<GradeBlock> gradeBlocks =
        blocks.stream()
            .map(block -> new GradeBlock(block, extractGradeTokens(block.text())))
            .filter(block -> !block.grades().isEmpty() && block.layout().left() > pageWidth * 0.55)
            .sorted(
                Comparator.comparingDouble((GradeBlock value) -> value.layout().centerY())
                    .reversed())
            .toList();
    if (gradeBlocks.isEmpty()) return List.of();
    double gradeColumnLeft =
        gradeBlocks.stream().mapToDouble(value -> value.layout().left()).min().orElse(pageWidth);
    List<LayoutText> subjects =
        blocks.stream()
            .filter(block -> block.left() < gradeColumnLeft * 0.85)
            .filter(block -> isSubjectCandidate(block.text()))
            .sorted(Comparator.comparingDouble(LayoutText::centerY).reversed())
            .limit(20)
            .toList();
    if (subjects.isEmpty()) return List.of();

    Map<Integer, String> gradeBySubjectIndex = new HashMap<>();
    for (GradeBlock gradeBlock : gradeBlocks) {
      double tolerance = Math.max(10.0, gradeBlock.layout().height() * 0.15);
      List<Integer> overlappingSubjectIndexes = new ArrayList<>();
      for (int index = 0; index < subjects.size(); index++) {
        double centerY = subjects.get(index).centerY();
        if (centerY >= gradeBlock.layout().bottom() - tolerance
            && centerY <= gradeBlock.layout().top() + tolerance) {
          overlappingSubjectIndexes.add(index);
        }
      }
      if (overlappingSubjectIndexes.size() == gradeBlock.grades().size()) {
        for (int index = 0; index < overlappingSubjectIndexes.size(); index++) {
          gradeBySubjectIndex.putIfAbsent(
              overlappingSubjectIndexes.get(index), gradeBlock.grades().get(index));
        }
      } else if (gradeBlock.grades().size() == 1) {
        nearestSubjectIndex(subjects, gradeBlock.layout().centerY())
            .filter(
                index ->
                    Math.abs(subjects.get(index).centerY() - gradeBlock.layout().centerY())
                        <= Math.max(subjects.get(index).height(), gradeBlock.layout().height())
                            * 1.5)
            .ifPresent(
                index -> gradeBySubjectIndex.putIfAbsent(index, gradeBlock.grades().getFirst()));
      } else {
        warnings.add(
            "A grouped qualification grade column could not be aligned safely; confirm the affected grades manually.");
      }
    }

    List<Map<String, Object>> results = new ArrayList<>();
    for (int index = 0; index < subjects.size(); index++) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("subjectName", normalizeQualificationText(subjects.get(index).text()));
      result.put("grade", gradeBySubjectIndex.getOrDefault(index, ""));
      results.add(result);
    }
    return List.copyOf(results);
  }

  private java.util.Optional<Integer> nearestSubjectIndex(
      List<LayoutText> subjects, double gradeCenterY) {
    Integer nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (int index = 0; index < subjects.size(); index++) {
      double distance = Math.abs(subjects.get(index).centerY() - gradeCenterY);
      if (distance < nearestDistance) {
        nearest = index;
        nearestDistance = distance;
      }
    }
    return java.util.Optional.ofNullable(nearest);
  }

  private List<String> extractGradeTokens(String rawText) {
    String normalized = rawText == null ? "" : rawText.trim().toUpperCase(Locale.ROOT);
    List<String> parentheticalGrades = new ArrayList<>();
    Matcher parentheticalMatcher = OCR_PARENTHETICAL_GRADE.matcher(normalized);
    int firstParentheticalStart = -1;
    int lastParentheticalEnd = -1;
    while (parentheticalMatcher.find()) {
      if (firstParentheticalStart < 0) firstParentheticalStart = parentheticalMatcher.start();
      parentheticalGrades.add(normalizeOcrGradeCharacter(parentheticalMatcher.group(1)));
      lastParentheticalEnd = parentheticalMatcher.end();
    }
    if (!parentheticalGrades.isEmpty()) {
      List<String> prefixGrades =
          extractIsolatedGradeTokens(normalized.substring(0, firstParentheticalStart));
      if (!prefixGrades.isEmpty()
          && prefixGrades.getLast().equals(parentheticalGrades.getFirst())) {
        prefixGrades = new ArrayList<>(prefixGrades.subList(0, prefixGrades.size() - 1));
      }
      List<String> grades = new ArrayList<>(prefixGrades);
      grades.addAll(parentheticalGrades);
      grades.addAll(extractIsolatedGradeTokens(normalized.substring(lastParentheticalEnd)));
      return grades.stream().limit(20).toList();
    }
    String withoutParentheticals = normalized.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", "");
    if (COMPACT_GRADES.matcher(withoutParentheticals).matches()) {
      return withoutParentheticals.chars().mapToObj(value -> String.valueOf((char) value)).toList();
    }
    return extractIsolatedGradeTokens(normalized);
  }

  private List<String> extractIsolatedGradeTokens(String value) {
    List<String> grades = new ArrayList<>();
    Matcher matcher = ISOLATED_GRADE.matcher(value);
    while (matcher.find()) grades.add(matcher.group(1).toUpperCase(Locale.ROOT));
    return grades;
  }

  private String normalizeOcrGradeCharacter(String value) {
    String gradeCharacter = value.toUpperCase(Locale.ROOT);
    if (gradeCharacter.equals("⊂")) return "C";
    if (gradeCharacter.equals("P")) return "D";
    return gradeCharacter;
  }

  private boolean isSubjectCandidate(String rawText) {
    String normalized = normalizeQualificationText(rawText);
    if (normalized.length() < 3 || normalized.length() > 80 || !normalized.matches(".*[A-Z].*")) {
      return false;
    }
    if (!extractGradeTokens(normalized).isEmpty()) return false;
    return NON_SUBJECT_MARKERS.stream().noneMatch(normalized::contains);
  }

  private String normalizeQualificationText(String value) {
    return value == null
        ? ""
        : value
            .replace('_', ' ')
            .replaceAll("^[#*\\-\\s]+", "")
            .replaceAll("\\s+", " ")
            .trim()
            .toUpperCase(Locale.ROOT);
  }

  private Double number(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  private Integer integer(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private boolean booleanValue(Object value) {
    return value instanceof Boolean booleanValue && booleanValue;
  }

  private boolean hasMissingQualificationGrade(Map<String, Object> facts) {
    Object resultsValue = facts.get("qualificationResults");
    if (!(resultsValue instanceof List<?> results)) return false;
    return results.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .anyMatch(result -> String.valueOf(result.getOrDefault("grade", "")).isBlank());
  }

  private record QualificationResultCandidate(
      List<Map<String, Object>> results, List<String> warnings) {}

  private void extractZimbabweNationalIdentityCard(
      String source, Map<String, Object> facts, Map<String, Double> confidence) {
    if (!ZIMBABWE_REPUBLIC.matcher(source).find()
        || !NATIONAL_REGISTRATION_HEADING.matcher(source).find()) return;
    facts.put("documentType", "ZIMBABWE_NATIONAL_ID");
    confidence.put("documentType", 0.98);
    Matcher identityNumber = ZIMBABWE_NATIONAL_ID.matcher(source);
    if (identityNumber.find()) {
      facts.put(
          "nationalIdNumber",
          identityNumber.group(1)
              + "-"
              + identityNumber.group(2)
              + identityNumber.group(3).toUpperCase(Locale.ROOT)
              + identityNumber.group(4));
      confidence.put("nationalIdNumber", 0.96);
    }
    Matcher citizenGender = ZIMBABWE_CITIZEN_GENDER.matcher(source);
    if (citizenGender.find()) {
      facts.put("genderCode", citizenGender.group(1).equalsIgnoreCase("M") ? "MALE" : "FEMALE");
      confidence.put("genderCode", 0.92);
    }
    facts.put("nationality", "Zimbabwe");
    facts.put("nationalityCode", "ZWE");
    confidence.put("nationality", 0.98);
    confidence.put("nationalityCode", 0.98);
  }

  private void extractPassportMachineReadableZone(
      String source,
      Map<String, Object> facts,
      Map<String, Double> confidence,
      List<String> warnings) {
    List<String> lines =
        source
            .lines()
            .map(line -> line.replaceAll("\\s+", "").toUpperCase(Locale.ROOT))
            .filter(line -> line.length() == 44)
            .toList();
    for (int index = 0; index + 1 < lines.size(); index++) {
      String firstLine = lines.get(index);
      String secondLine = lines.get(index + 1);
      if (!firstLine.startsWith("P<") || !validTd3Checksums(secondLine)) continue;
      facts.put("documentType", "ICAO_TD3_PASSPORT");
      confidence.put("documentType", 0.99);
      String passportNumber = secondLine.substring(0, 9).replace("<", "");
      facts.put("passportNumber", passportNumber);
      confidence.put("passportNumber", 0.98);
      facts.put("nationalityCode", secondLine.substring(10, 13));
      confidence.put("nationalityCode", 0.99);
      facts.put("dateOfBirth", formatMrzBirthDate(secondLine.substring(13, 19)));
      confidence.put("dateOfBirth", 0.96);
      String genderMarker = secondLine.substring(20, 21);
      if (genderMarker.equals("M") || genderMarker.equals("F")) {
        facts.put("genderCode", genderMarker.equals("M") ? "MALE" : "FEMALE");
        confidence.put("genderCode", 0.98);
      }
      String[] nameParts = firstLine.substring(5).split("<<", 2);
      String surname = cleanMrzName(nameParts[0]);
      if (!surname.isBlank()) {
        facts.put("lastName", surname);
        confidence.put("lastName", 0.95);
      }
      if (nameParts.length == 2) {
        List<String> givenNames =
            Arrays.stream(nameParts[1].split("<+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (!givenNames.isEmpty()) {
          facts.put("firstName", givenNames.getFirst());
          confidence.put("firstName", 0.95);
          if (givenNames.size() > 1) {
            facts.put("middleNames", String.join(" ", givenNames.subList(1, givenNames.size())));
            confidence.put("middleNames", 0.92);
          }
        }
      }
      return;
    }
    if (lines.stream().anyMatch(line -> line.startsWith("P<"))) {
      warnings.add(
          "A passport machine-readable zone was detected but its check digits did not validate; verify the passport manually.");
    }
  }

  private boolean validTd3Checksums(String line) {
    if (line.length() != 44) return false;
    return validMrzCheckDigit(line.substring(0, 9), line.charAt(9))
        && validMrzCheckDigit(line.substring(13, 19), line.charAt(19))
        && validMrzCheckDigit(line.substring(21, 27), line.charAt(27))
        && validMrzCheckDigit(
            line.substring(0, 10) + line.substring(13, 20) + line.substring(21, 43),
            line.charAt(43));
  }

  private boolean validMrzCheckDigit(String value, char expected) {
    if (!Character.isDigit(expected)) return false;
    int[] weights = {7, 3, 1};
    int total = 0;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      int numericValue;
      if (Character.isDigit(character)) numericValue = character - '0';
      else if (character >= 'A' && character <= 'Z') numericValue = character - 'A' + 10;
      else if (character == '<') numericValue = 0;
      else return false;
      total += numericValue * weights[index % weights.length];
    }
    return total % 10 == expected - '0';
  }

  private String formatMrzBirthDate(String value) {
    int twoDigitYear = Integer.parseInt(value.substring(0, 2));
    int currentTwoDigitYear = LocalDate.now(ZoneOffset.UTC).getYear() % 100;
    int year = twoDigitYear <= currentTwoDigitYear ? 2000 + twoDigitYear : 1900 + twoDigitYear;
    return value.substring(4, 6) + "/" + value.substring(2, 4) + "/" + year;
  }

  private String cleanMrzName(String value) {
    return value.replace('<', ' ').replaceAll("\\s+", " ").trim();
  }

  private void propose(
      Map<String, Object> facts,
      Map<String, Double> confidence,
      String key,
      Pattern pattern,
      String text,
      double confidenceValue) {
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      facts.putIfAbsent(key, matcher.group(1).trim());
      confidence.putIfAbsent(key, confidenceValue);
    }
  }

  public record ExtractionFacts(
      Map<String, Object> facts, Map<String, Double> confidence, List<String> warnings) {}

  private record LayoutText(String text, double left, double bottom, double right, double top) {
    double centerY() {
      return (bottom + top) / 2.0;
    }

    double height() {
      return Math.max(1.0, Math.abs(top - bottom));
    }
  }

  private record GradeBlock(LayoutText layout, List<String> grades) {}

  private record TableCell(String text, int column, boolean rowHeader) {}
}
