package zw.ac.uz.emhare.studentrecords.reporting.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationOutcome;

/** Read-only Student Records projection for cross-service admissions reporting. @author Tinashe K */
@Repository
public class AdmissionsRegistrationReportingRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionsRegistrationReportingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionsRegistrationOutcome> findOutcomes() {
        return jdbcTemplate.query("""
                SELECT student.source_application_id,
                       student.source_offer_id,
                       student.id AS student_id,
                       student.student_number,
                       enrolment.programme_id,
                       enrolment.programme_code,
                       enrolment.programme_name,
                       enrolment.intake_id,
                       registration.status AS registration_status,
                       registration.confirmed_at AS registration_confirmed_at
                  FROM students student
                  JOIN student_programme_enrolments enrolment
                    ON enrolment.student_id = student.id
                   AND enrolment.deleted_at IS NULL
                  LEFT JOIN LATERAL (
                       SELECT session.status, session.confirmed_at
                         FROM registration_sessions session
                        WHERE session.student_id = student.id
                          AND session.programme_enrolment_id = enrolment.id
                          AND session.deleted_at IS NULL
                        ORDER BY session.initiated_at DESC
                        LIMIT 1
                  ) registration ON TRUE
                 WHERE student.deleted_at IS NULL
                 ORDER BY student.source_application_id, enrolment.programme_code
                """, AdmissionsRegistrationReportingRepository::mapOutcome);
    }

    private static AdmissionsRegistrationOutcome mapOutcome(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp confirmedAt = resultSet.getTimestamp("registration_confirmed_at");
        return new AdmissionsRegistrationOutcome(
                resultSet.getObject("source_application_id", java.util.UUID.class),
                resultSet.getObject("source_offer_id", java.util.UUID.class),
                resultSet.getObject("student_id", java.util.UUID.class),
                resultSet.getString("student_number"),
                resultSet.getObject("programme_id", java.util.UUID.class),
                resultSet.getString("programme_code"),
                resultSet.getString("programme_name"),
                resultSet.getObject("intake_id", java.util.UUID.class),
                resultSet.getString("registration_status"),
                confirmedAt == null ? null : confirmedAt.toInstant());
    }
}
