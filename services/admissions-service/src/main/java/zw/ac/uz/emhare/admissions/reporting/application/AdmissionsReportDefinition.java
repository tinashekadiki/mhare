package zw.ac.uz.emhare.admissions.reporting.application;

import java.util.List;

/** User-facing catalogue entry with deliberately restricted output formats. @author Tinashe K */
public record AdmissionsReportDefinition(
        AdmissionsReportCode code,
        String family,
        String title,
        String description,
        List<String> formats,
        List<String> variants) {

    public AdmissionsReportDefinition {
        formats = List.copyOf(formats);
        variants = List.copyOf(variants);
    }
}
