package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** @author Tinashe K */
class StudentNumberGeneratorTest {

    @Test
    void allocatesAndFormatsAStudentNumberForItsPrefixAndCohort() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("R"), eq(2026)))
                .thenReturn(42L);
        StudentNumberGenerator generator = new StudentNumberGenerator(
                jdbcTemplate, new StudentReferenceNumberProperties());

        String studentNumber = generator.nextStudentNumber("LOCAL", 2026);

        assertEquals("R260042", studentNumber.substring(0, 7));
    }

    @Test
    void rejectsACounterAllocationThatReturnsNoValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("R"), eq(2026)))
                .thenReturn(null);
        StudentNumberGenerator generator = new StudentNumberGenerator(
                jdbcTemplate, new StudentReferenceNumberProperties());

        assertThrows(IllegalStateException.class, () -> generator.nextStudentNumber("LOCAL", 2026));
    }
}
