package zw.ac.uz.emhare.studentrecords.conversion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
public class StudentNumberGenerator {

    private static final String ALLOCATE_NUMBER_SQL = """
            INSERT INTO student_number_counters (number_prefix, cohort_year, next_value)
            VALUES (?, ?, 1)
            ON CONFLICT (number_prefix, cohort_year)
            DO UPDATE SET next_value = student_number_counters.next_value + 1
            RETURNING next_value - 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final StudentReferenceNumberProperties properties;

    public StudentNumberGenerator(JdbcTemplate jdbcTemplate, StudentReferenceNumberProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public String nextStudentNumber(String applicantCategoryCode, int cohortYear) {
        String prefix = properties.prefixFor(applicantCategoryCode);
        Long allocation = jdbcTemplate.queryForObject(
                ALLOCATE_NUMBER_SQL, Long.class, prefix, cohortYear);
        if (allocation == null) {
            throw new IllegalStateException("Student number counter did not return an allocation.");
        }
        return properties.format(prefix, cohortYear, allocation);
    }
}
