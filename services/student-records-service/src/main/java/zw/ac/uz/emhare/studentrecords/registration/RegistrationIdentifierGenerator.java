package zw.ac.uz.emhare.studentrecords.registration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
public class RegistrationIdentifierGenerator {

    private final JdbcTemplate jdbcTemplate;

    public RegistrationIdentifierGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextRegistrationNumber() {
        Long value = jdbcTemplate.queryForObject("select nextval('registration_number_sequence')", Long.class);
        if (value == null) {
            throw new IllegalStateException("Registration identifier sequence did not return a value.");
        }
        return "REG-%08d".formatted(value);
    }
}
