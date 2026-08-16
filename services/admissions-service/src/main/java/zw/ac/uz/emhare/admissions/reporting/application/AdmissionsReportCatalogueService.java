package zw.ac.uz.emhare.admissions.reporting.application;

import java.util.List;
import org.springframework.stereotype.Service;

/** Canonical new-eMhare replacement for the legacy admissions report menu. @author Tinashe K */
@Service
public class AdmissionsReportCatalogueService {

    private static final List<AdmissionsReportDefinition> CATALOGUE = List.of(
            report(AdmissionsReportCode.APPLICATION_DEMAND, "Application demand",
                    "Programme and academic-unit demand",
                    "Compare distinct applications and ranked Programme choices without multiplying applicant totals.",
                    List.of("SCREEN", "BAR_CHART", "XLSX", "PDF"),
                    "Programme application report", "Academic-unit Programme statistics",
                    "Ranked-choice demand by gender", "Offered, accepted and pending-approval counts"),
            report(AdmissionsReportCode.EXECUTIVE_STATISTICS, "Executive statistics",
                    "Admissions executive statistics",
                    "Outcome and conversion totals by Programme, academic unit, intake, date and year.",
                    List.of("SCREEN", "XLSX", "PDF"),
                    "Accepted-applicant statistics", "Applicants offered places by Programme",
                    "Accepted versus registered", "Applicant totals by intake, date and year"),
            report(AdmissionsReportCode.APPLICANT_REGISTERS, "Applicant registers",
                    "Applicant status registers",
                    "Operational applicant lists with explicit current-state inclusion rules.",
                    List.of("SCREEN", "XLSX", "PDF"),
                    "Applied", "Applying", "Unconfirmed", "Not yet selected", "Selected",
                    "Accepted", "Rejected", "Not selected"),
            report(AdmissionsReportCode.SPECIAL_CATEGORY_REGISTERS, "Special-category registers",
                    "Special-category applicant registers",
                    "Disability and staff-dependant registers derived from governed applicant profile fields.",
                    List.of("SCREEN", "XLSX", "PDF"),
                    "Applicants with disabilities", "Staff dependants",
                    "Disabled applicants accepted", "Disabled applicants not accepted"),
            report(AdmissionsReportCode.SELECTION_SCHEDULES, "Selection schedules",
                    "Selection and education schedules",
                    "Decision-backed schedules with qualification evidence for the rolling admissions workflow.",
                    List.of("SCREEN", "XLSX", "PDF"),
                    "Undergraduate rolling admissions (legacy supplementary replacement)", "Undergraduate SAR",
                    "Postgraduate SAR", "Trimmed Programme lists", "Selected-applicant education report"),
            report(AdmissionsReportCode.INTAKE_MOVEMENTS, "Intake movements",
                    "Applicant intake movements",
                    "Audited before-and-after intake changes reconstructed from immutable revision history.",
                    List.of("SCREEN", "XLSX", "PDF"),
                    "Accepted applicants moved between intakes", "Undecided applicants moved between intakes"),
            report(AdmissionsReportCode.ADMISSIONS_ANALYSIS, "Analysis",
                    "Admissions trend analysis",
                    "Application, applicant, choice and acceptance trends across the reusable admissions dimensions.",
                    List.of("SCREEN", "GRAPH", "XLSX", "PDF"),
                    "Academic-unit trends", "Programme trends", "Intake trends", "Gender trends",
                    "First, second and third choice", "School analysis", "Applicant summaries"),
            report(AdmissionsReportCode.OFFER_LETTERS, "Offer letters",
                    "Governed offer-letter operations",
                    "Generate, publish, download and email current versioned offer letters through the offer workspace.",
                    List.of("PDF", "EMAIL"),
                    "Local", "International", "Undergraduate", "Postgraduate", "Transfer",
                    "Single generation", "Bulk generation", "Print", "Email"));

    public List<AdmissionsReportDefinition> catalogue() {
        return CATALOGUE;
    }

    public AdmissionsReportDefinition require(AdmissionsReportCode code) {
        return CATALOGUE.stream().filter(report -> report.code() == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown admissions report."));
    }

    private static AdmissionsReportDefinition report(
            AdmissionsReportCode code,
            String family,
            String title,
            String description,
            List<String> formats,
            String... variants) {
        return new AdmissionsReportDefinition(code, family, title, description, formats, List.of(variants));
    }
}
