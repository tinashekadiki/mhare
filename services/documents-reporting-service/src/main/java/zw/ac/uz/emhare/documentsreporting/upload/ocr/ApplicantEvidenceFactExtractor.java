package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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

  public ExtractionFacts extract(String text) {
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
    facts.put(
        "lines",
        source.lines().map(String::trim).filter(line -> !line.isBlank()).limit(300).toList());
    warnings.add(
        facts.size() <= 1
            ? "No high-confidence identity facts were detected; complete the form manually."
            : "OCR values are proposals and must be checked against the uploaded evidence.");
    return new ExtractionFacts(facts, confidence, List.copyOf(warnings));
  }

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
}
