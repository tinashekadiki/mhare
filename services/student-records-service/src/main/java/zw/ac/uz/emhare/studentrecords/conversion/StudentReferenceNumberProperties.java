package zw.ac.uz.emhare.studentrecords.conversion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
@ConfigurationProperties(prefix = "emhare.reference-numbers.student")
public class StudentReferenceNumberProperties {

    private static final Set<Character> AMBIGUOUS_CHECK_LETTERS = Set.of('B', 'G', 'I', 'L', 'O', 'Q', 'S', 'Z');

    private String localPrefix = "R";
    private String foreignPrefix = "FR";
    private String localCategoryCodes = "LOCAL";
    private int yearDigits = 2;
    private int serialDigits = 4;
    private String checkLetters = "ACDEFHJKMNPRTUVWXY";
    private String format = "{prefix}{year}{serial}{check}";

    public String prefixFor(String applicantCategoryCode) {
        String normalizedCategory = required(applicantCategoryCode, "Applicant category").toUpperCase(Locale.ROOT);
        Set<String> localCategories = Arrays.stream(required(localCategoryCodes, "Local category codes").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return required(localCategories.contains(normalizedCategory) ? localPrefix : foreignPrefix, "Student number prefix")
                .toUpperCase(Locale.ROOT);
    }

    public String format(String prefix, int cohortYear, long allocation) {
        if (yearDigits < 1 || yearDigits > 4) {
            throw new IllegalStateException("Student number year digits must be between 1 and 4.");
        }
        if (serialDigits < 1 || serialDigits > 9) {
            throw new IllegalStateException("Student number serial digits must be between 1 and 9.");
        }
        String safeCheckLetters = validateCheckLetters();
        long serialRange = powerOfTen(serialDigits);
        long totalCapacity = Math.multiplyExact(serialRange, safeCheckLetters.length());
        if (allocation < 0 || allocation >= totalCapacity) {
            throw new IllegalStateException("Student number capacity is exhausted for " + prefix + cohortYear + ".");
        }
        long serialValue = allocation % serialRange;
        long overflowRound = allocation / serialRange;
        String year = "%0" + yearDigits + "d";
        year = year.formatted(Math.floorMod(cohortYear, (int) powerOfTen(yearDigits)));
        String serial = ("%0" + serialDigits + "d").formatted(serialValue);
        int baseCheckIndex = checksum(prefix + year + serial) % safeCheckLetters.length();
        char checkLetter = safeCheckLetters.charAt((int) ((baseCheckIndex + overflowRound) % safeCheckLetters.length()));
        String configuredFormat = required(format, "Student number format");
        for (String placeholder : Set.of("{prefix}", "{year}", "{serial}", "{check}")) {
            if (!configuredFormat.contains(placeholder)) {
                throw new IllegalStateException("Student number format must contain " + placeholder + ".");
            }
        }
        return configuredFormat
                .replace("{prefix}", required(prefix, "Student number prefix"))
                .replace("{year}", year)
                .replace("{serial}", serial)
                .replace("{check}", Character.toString(checkLetter));
    }

    private String validateCheckLetters() {
        String normalized = required(checkLetters, "Student number check letters").toUpperCase(Locale.ROOT);
        if (normalized.chars().anyMatch(value -> !Character.isLetter(value))) {
            throw new IllegalStateException("Student number check letters may contain letters only.");
        }
        if (normalized.chars().distinct().count() != normalized.length()) {
            throw new IllegalStateException("Student number check letters must be unique.");
        }
        if (normalized.chars().mapToObj(value -> (char) value).anyMatch(AMBIGUOUS_CHECK_LETTERS::contains)) {
            throw new IllegalStateException("Student number check letters cannot contain visually ambiguous letters.");
        }
        return normalized;
    }

    private static int checksum(String value) {
        int checksum = 0;
        for (int index = 0; index < value.length(); index++) checksum += value.charAt(index) * (index + 1);
        return Math.floorMod(checksum, Integer.MAX_VALUE);
    }

    private static long powerOfTen(int digits) {
        long value = 1;
        for (int index = 0; index < digits; index++) value = Math.multiplyExact(value, 10);
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is required.");
        return value.trim();
    }

    public void setLocalPrefix(String localPrefix) { this.localPrefix = localPrefix; }
    public void setForeignPrefix(String foreignPrefix) { this.foreignPrefix = foreignPrefix; }
    public void setLocalCategoryCodes(String localCategoryCodes) { this.localCategoryCodes = localCategoryCodes; }
    public void setYearDigits(int yearDigits) { this.yearDigits = yearDigits; }
    public void setSerialDigits(int serialDigits) { this.serialDigits = serialDigits; }
    public void setCheckLetters(String checkLetters) { this.checkLetters = checkLetters; }
    public void setFormat(String format) { this.format = format; }
}
