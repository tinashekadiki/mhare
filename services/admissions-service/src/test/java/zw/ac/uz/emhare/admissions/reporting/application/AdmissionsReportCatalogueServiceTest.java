package zw.ac.uz.emhare.admissions.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class AdmissionsReportCatalogueServiceTest {

    @Test
    void exposesEveryApprovedAdmissionsReportFamilyWithGovernedFormats() {
        AdmissionsReportCatalogueService service = new AdmissionsReportCatalogueService();

        List<AdmissionsReportDefinition> reports = service.catalogue();

        assertThat(reports).extracting(AdmissionsReportDefinition::code).containsExactly(
                AdmissionsReportCode.APPLICATION_DEMAND,
                AdmissionsReportCode.EXECUTIVE_STATISTICS,
                AdmissionsReportCode.APPLICANT_REGISTERS,
                AdmissionsReportCode.SPECIAL_CATEGORY_REGISTERS,
                AdmissionsReportCode.SELECTION_SCHEDULES,
                AdmissionsReportCode.INTAKE_MOVEMENTS,
                AdmissionsReportCode.ADMISSIONS_ANALYSIS,
                AdmissionsReportCode.OFFER_LETTERS);
        assertThat(reports.get(1).formats()).containsExactly("SCREEN", "XLSX", "PDF");
        assertThat(reports.get(2).formats()).containsExactly("SCREEN", "XLSX");
        assertThat(reports.get(7).formats()).containsExactly("PDF", "EMAIL");
    }
}
