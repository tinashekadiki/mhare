package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;

import java.util.ArrayList;
import java.util.List;

/** Default section plan for an application type. @author Tinashe K */
public record ApplicationSectionTemplate(
        String code,
        String name,
        boolean required,
        boolean repeatable,
        int minimumRecords,
        int sortOrder) {

    public static List<ApplicationSectionTemplate> defaults(ApplicationType applicationType) {
        List<ApplicationSectionTemplate> sections = new ArrayList<>();
        sections.add(section("PERSONAL_DETAILS", "Applicant details", false, 0, 10));
        sections.add(section("NEXT_OF_KIN", "Next of kin", true, 1, 20));
        sections.add(section("QUALIFICATIONS", "Qualifications", true, 1, 30));
        if (applicationType.requiresEmploymentHistory()) {
            sections.add(section("EMPLOYMENT_HISTORY", "Employment history", true, 1, 40));
        }
        if (applicationType.requiresReferees()) {
            sections.add(section("REFEREES", "Referees", true, 2, 50));
        }
        sections.add(section("PROGRAMME_CHOICES", "Programme choices", true, 1, 60));
        sections.add(section("DOCUMENTS", "Supporting documents", true, 0, 70));
        sections.add(section("PAYMENT", "Application fee", false, 0, 80));
        sections.add(section("REVIEW_DECLARATION", "Review and declaration", false, 0, 90));
        return List.copyOf(sections);
    }

    private static ApplicationSectionTemplate section(
            String code, String name, boolean repeatable, int minimumRecords, int sortOrder) {
        return new ApplicationSectionTemplate(code, name, true, repeatable, minimumRecords, sortOrder);
    }
}
