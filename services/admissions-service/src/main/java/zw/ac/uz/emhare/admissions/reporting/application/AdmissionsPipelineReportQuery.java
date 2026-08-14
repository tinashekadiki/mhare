package zw.ac.uz.emhare.admissions.reporting.application;

import java.util.Locale;
import java.util.UUID;

/** Filters for the admissions pipeline operational report. @author Tinashe K */
public record AdmissionsPipelineReportQuery(
        UUID intakeId,
        UUID programmeId,
        UUID applicationTypeId,
        String categoryCode,
        String genderCode) {

    public static AdmissionsPipelineReportQuery empty() {
        return new AdmissionsPipelineReportQuery(null, null, null, null, null);
    }

    public static AdmissionsPipelineReportQuery of(
            UUID intakeId,
            UUID programmeId,
            UUID applicationTypeId,
            String categoryCode,
            String genderCode) {
        return new AdmissionsPipelineReportQuery(
                intakeId,
                programmeId,
                applicationTypeId,
                normalizeCode(categoryCode),
                normalizeCode(genderCode));
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
