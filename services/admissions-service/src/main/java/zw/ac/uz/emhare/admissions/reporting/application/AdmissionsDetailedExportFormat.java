package zw.ac.uz.emhare.admissions.reporting.application;

import java.util.Locale;

/** Supported detailed admissions report formats. @author Tinashe K */
public enum AdmissionsDetailedExportFormat {
    CSV,
    XLSX,
    PDF;

    public static AdmissionsDetailedExportFormat from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("An export format is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Supported export formats are CSV, XLSX and PDF.", exception);
        }
    }
}
