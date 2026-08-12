package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdmissionsIdentifierGenerator {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionsIdentifierGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextApplicantNumber() {
        return "APP-%08d".formatted(nextSequenceValue("applicant_number_sequence"));
    }

    public String nextApplicationNumber(AdmissionCycle admissionCycle) {
        String cycleCode = admissionCycle.getCode()
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return "EMH-%s-%08d".formatted(cycleCode, nextSequenceValue("application_number_sequence"));
    }

    public String nextOfferNumber(AdmissionCycle admissionCycle) {
        String cycleCode = admissionCycle.getCode()
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
