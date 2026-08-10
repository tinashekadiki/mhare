package zw.ac.uz.emhare.admissions.application;

import java.util.Locale;

public enum ApplicantCategoryCode {
    LOCAL("Local applicant"),
    SADC("SADC applicant"),
    INTERNATIONAL("International applicant"),
    CLE("Continuing legal education applicant");

    private final String label;

    ApplicantCategoryCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ApplicantCategoryCode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Applicant category is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported applicant category: " + value.trim(), exception);
        }
    }
}
