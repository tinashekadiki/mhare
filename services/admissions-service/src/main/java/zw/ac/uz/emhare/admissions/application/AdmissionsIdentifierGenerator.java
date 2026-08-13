package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdmissionsIdentifierGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicantReferenceNumberProperties applicantReferenceNumberProperties;

    public AdmissionsIdentifierGenerator(
            JdbcTemplate jdbcTemplate,
            ApplicantReferenceNumberProperties applicantReferenceNumberProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicantReferenceNumberProperties = applicantReferenceNumberProperties;
    }

    public String nextApplicantNumber() {
        return applicantReferenceNumberProperties.format(nextSequenceValue("applicant_number_sequence"));
    }

    public String nextApplicationNumber(AdmissionCycle admissionCycle) {
        return nextApplicationNumber(admissionCycle.getCode());
    }

    public String nextApplicationNumber(String intakeCode) {
        String cycleCode = intakeCode
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return "EMH-%s-%08d".formatted(cycleCode, nextSequenceValue("application_number_sequence"));
    }

    public String nextOfferNumber(AdmissionCycle admissionCycle) {
        return nextOfferNumber(admissionCycle.getCode());
    }

    public String nextOfferNumber(String intakeCode) {
        String cycleCode = intakeCode
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return "OFR-%s-%08d".formatted(cycleCode, nextSequenceValue("offer_number_sequence"));
    }

    private long nextSequenceValue(String sequenceName) {
        Long value = jdbcTemplate.queryForObject("select nextval('" + sequenceName + "')", Long.class);
        if (value == null) {
            throw new IllegalStateException("Admissions identifier sequence did not return a value.");
        }
        return value;
    }
}
