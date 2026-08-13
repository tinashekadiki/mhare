package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** @author Tinashe K */
class AdmissionsIdentifierGeneratorTest {

    @Test
    void generatesTheDefaultSixDigitApplicantReference() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select nextval('applicant_number_sequence')", Long.class))
                .thenReturn(42L);

        AdmissionsIdentifierGenerator generator = new AdmissionsIdentifierGenerator(
                jdbcTemplate, new ApplicantReferenceNumberProperties());

        assertEquals("A000042", generator.nextApplicantNumber());
    }

    @Test
    void appliesACustomApplicantReferenceFormat() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select nextval('applicant_number_sequence')", Long.class))
                .thenReturn(42L);
        ApplicantReferenceNumberProperties properties = new ApplicantReferenceNumberProperties();
        properties.setPrefix("apl-");
        properties.setSequenceDigits(8);
        properties.setFormat("{prefix}{sequence}");

        AdmissionsIdentifierGenerator generator = new AdmissionsIdentifierGenerator(jdbcTemplate, properties);

        assertEquals("APL-00000042", generator.nextApplicantNumber());
    }

    @Test
    void rejectsInvalidApplicantReferenceConfigurationAndExhaustedSequences() {
        ApplicantReferenceNumberProperties properties = new ApplicantReferenceNumberProperties();

        properties.setSequenceDigits(0);
        assertThrows(IllegalStateException.class, () -> properties.format(1));
        properties.setSequenceDigits(13);
        assertThrows(IllegalStateException.class, () -> properties.format(1));
        properties.setSequenceDigits(6);
        assertThrows(IllegalStateException.class, () -> properties.format(-1));
        assertThrows(IllegalStateException.class, () -> properties.format(1_000_000));

        properties.setPrefix(null);
        assertThrows(IllegalStateException.class, () -> properties.format(1));
        properties.setPrefix("  ");
        assertThrows(IllegalStateException.class, () -> properties.format(1));
        properties.setPrefix("A");
        properties.setFormat(null);
        assertThrows(IllegalStateException.class, () -> properties.format(1));
        properties.setFormat("{prefix}");
        assertThrows(IllegalStateException.class, () -> properties.format(1));
    }

    @Test
    void rejectsAnApplicantSequenceThatReturnsNoValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select nextval('applicant_number_sequence')", Long.class))
                .thenReturn(null);
        AdmissionsIdentifierGenerator generator = new AdmissionsIdentifierGenerator(
                jdbcTemplate, new ApplicantReferenceNumberProperties());

        assertThrows(IllegalStateException.class, generator::nextApplicantNumber);
    }
}
