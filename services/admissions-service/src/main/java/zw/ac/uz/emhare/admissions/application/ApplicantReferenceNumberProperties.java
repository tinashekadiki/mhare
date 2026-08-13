package zw.ac.uz.emhare.admissions.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
@ConfigurationProperties(prefix = "emhare.reference-numbers.applicant")
public class ApplicantReferenceNumberProperties {

    private String prefix = "A";
    private int sequenceDigits = 6;
    private String format = "{prefix}{sequence}";

    public String format(long sequenceValue) {
        if (sequenceDigits < 1 || sequenceDigits > 12) {
            throw new IllegalStateException("Applicant reference sequence digits must be between 1 and 12.");
        }
        long maximumSequenceValue = maximumValue(sequenceDigits);
        if (sequenceValue < 0 || sequenceValue > maximumSequenceValue) {
            throw new IllegalStateException("Applicant reference sequence has exhausted its configured numeric range.");
        }
        String normalizedPrefix = required(prefix, "Applicant reference prefix").toUpperCase(java.util.Locale.ROOT);
        String configuredFormat = required(format, "Applicant reference format");
        if (!configuredFormat.contains("{sequence}")) {
            throw new IllegalStateException("Applicant reference format must contain {sequence}.");
        }
        return configuredFormat
                .replace("{prefix}", normalizedPrefix)
                .replace("{sequence}", ("%0" + sequenceDigits + "d").formatted(sequenceValue));
    }

    private static long maximumValue(int digits) {
        long maximum = 0;
        for (int index = 0; index < digits; index++) maximum = maximum * 10 + 9;
        return maximum;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is required.");
        return value.trim();
    }

    public void setPrefix(String prefix) { this.prefix = prefix; }
    public void setSequenceDigits(int sequenceDigits) { this.sequenceDigits = sequenceDigits; }
    public void setFormat(String format) { this.format = format; }
}
