package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportQuery;

/** Canonical Admissions-owned evidence projection for operational reports. @author Tinashe K */
@Repository
public class AdmissionsOperationalReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionsOperationalReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionsOperationalReportRow> findRows(AdmissionsPipelineReportQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT application_record.id AS application_id,
                       applicant.id AS applicant_id,
                       application_record.application_number,
                       applicant.applicant_number,
                       TRIM(CONCAT_WS(' ', applicant.first_name,
                            NULLIF(TRIM(applicant.middle_names), ''), applicant.last_name)) AS applicant_name,
                       application_record.submitted_at,
                       application_record.status AS application_status,
                       application_record.calculated_total_points,
                       application_record.intake_id,
                       application_record.intake_code,
                       application_record.intake_name,
                       application_type.id AS application_type_id,
                       application_type.code AS application_type_code,
                       application_type.name AS application_type_name,
                       applicant.applicant_category_code,
                       applicant.gender_code,
                       applicant.disability_status_code,
                       applicant.sponsor_type_code,
                       programme_choice.id AS choice_id,
                       programme_choice.choice_rank,
                       programme_choice.choice_status,
                       programme_choice.programme_id,
                       programme_choice.programme_code,
                       programme_choice.programme_name,
                       programme_choice.owning_academic_unit_name,
                       qualification.qualification_summary,
                       qualification.schools_attended,
                       choice_decision.decision,
                       choice_decision.decided_at,
                       admission_offer.id AS offer_id,
                       admission_offer.offer_number,
                       admission_offer.status AS offer_status,
                       admission_offer.offer_type,
                       publication.portal_published_at,
                       publication.email_delivery_status,
                       offer_response.response AS offer_response
                  FROM applications application_record
                  JOIN applicants applicant
                    ON applicant.id = application_record.applicant_id
                   AND applicant.deleted_at IS NULL
                  JOIN application_types application_type
                    ON application_type.id = application_record.application_type_id
                  LEFT JOIN application_programme_choices programme_choice
                    ON programme_choice.application_id = application_record.id
                   AND programme_choice.deleted_at IS NULL
                  LEFT JOIN programme_choice_decisions choice_decision
                    ON choice_decision.programme_choice_id = programme_choice.id
                   AND choice_decision.deleted_at IS NULL
                  LEFT JOIN offers admission_offer
                    ON admission_offer.programme_choice_id = programme_choice.id
                   AND admission_offer.deleted_at IS NULL
                  LEFT JOIN offer_publications publication
                    ON publication.id = admission_offer.current_publication_id
                   AND publication.deleted_at IS NULL
                  LEFT JOIN offer_responses offer_response
                    ON offer_response.offer_id = admission_offer.id
                   AND offer_response.deleted_at IS NULL
                  LEFT JOIN LATERAL (
                       SELECT STRING_AGG(DISTINCT CONCAT_WS(' ', sitting.level, sitting.year_written::text,
                                  NULLIF(result.subject_name_snapshot, ''), NULLIF(result.grade, '')), ' | '
                                  ORDER BY CONCAT_WS(' ', sitting.level, sitting.year_written::text,
                                  NULLIF(result.subject_name_snapshot, ''), NULLIF(result.grade, '')))
                                  AS qualification_summary,
                              STRING_AGG(DISTINCT NULLIF(TRIM(sitting.institution_name), ''), ' | '
                                  ORDER BY NULLIF(TRIM(sitting.institution_name), ''))
                                  AS schools_attended
                         FROM applicant_qualification_sittings sitting
                         LEFT JOIN applicant_qualification_results result
                           ON result.qualification_sitting_id = sitting.id
                          AND result.deleted_at IS NULL
                        WHERE sitting.application_id = application_record.id
                          AND sitting.deleted_at IS NULL
                  ) qualification ON TRUE
                 WHERE application_record.deleted_at IS NULL
                """);
        List<Object> arguments = new ArrayList<>();
        appendUuid(sql, arguments, "application_record.intake_id", query.intakeId());
        appendUuid(sql, arguments, "application_record.application_type_id", query.applicationTypeId());
        appendText(sql, arguments, "applicant.applicant_category_code", query.categoryCode());
        appendText(sql, arguments, "applicant.gender_code", query.genderCode());
        appendUuid(sql, arguments, "programme_choice.programme_id", query.programmeId());
        sql.append(" ORDER BY applicant.last_name, applicant.first_name, application_record.application_number, programme_choice.choice_rank");
        return jdbcTemplate.query(sql.toString(), AdmissionsOperationalReportRepository::mapRow, arguments.toArray());
    }

    public List<AdmissionsIntakeMovementRow> findIntakeMovements(AdmissionsPipelineReportQuery query) {
        StringBuilder sql = new StringBuilder("""
                WITH revisions AS (
                    SELECT audited.id AS application_id,
                           audited.intake_id,
                           audited.intake_code,
                           audited.intake_name,
                           audited.rev,
                           LAG(audited.intake_id) OVER (PARTITION BY audited.id ORDER BY audited.rev) AS previous_intake_id,
                           LAG(audited.intake_code) OVER (PARTITION BY audited.id ORDER BY audited.rev) AS previous_intake_code,
                           LAG(audited.intake_name) OVER (PARTITION BY audited.id ORDER BY audited.rev) AS previous_intake_name
                      FROM applications_aud audited
                     WHERE audited.revtype <> 2
                )
                SELECT application_record.id AS application_id,
                       application_record.application_number,
                       applicant.applicant_number,
                       TRIM(CONCAT_WS(' ', applicant.first_name,
                            NULLIF(TRIM(applicant.middle_names), ''), applicant.last_name)) AS applicant_name,
                       application_record.status AS application_status,
                       revisions.previous_intake_id,
                       revisions.previous_intake_code,
                       revisions.previous_intake_name,
                       revisions.intake_id AS new_intake_id,
                       revisions.intake_code AS new_intake_code,
                       revisions.intake_name AS new_intake_name,
                       revision.actor_user_id AS changed_by_user_id,
                       TO_TIMESTAMP(revision.revtstmp / 1000.0) AS changed_at,
                       revision.reason
                  FROM revisions
                  JOIN applications application_record ON application_record.id = revisions.application_id
                  JOIN applicants applicant ON applicant.id = application_record.applicant_id
                  JOIN revinfo revision ON revision.rev = revisions.rev
                 WHERE revisions.previous_intake_id IS NOT NULL
                   AND revisions.previous_intake_id <> revisions.intake_id
                   AND application_record.deleted_at IS NULL
                """);
        List<Object> arguments = new ArrayList<>();
        appendUuid(sql, arguments, "application_record.intake_id", query.intakeId());
        appendUuid(sql, arguments, "application_record.application_type_id", query.applicationTypeId());
        appendText(sql, arguments, "applicant.applicant_category_code", query.categoryCode());
        appendText(sql, arguments, "applicant.gender_code", query.genderCode());
        if (query.programmeId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM application_programme_choices choice_record WHERE choice_record.application_id = application_record.id AND choice_record.programme_id = ? AND choice_record.deleted_at IS NULL)");
            arguments.add(query.programmeId());
        }
        sql.append(" ORDER BY changed_at DESC, application_record.application_number");
        return jdbcTemplate.query(sql.toString(), AdmissionsOperationalReportRepository::mapMovement, arguments.toArray());
    }

    private static AdmissionsOperationalReportRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdmissionsOperationalReportRow(
                uuid(resultSet, "application_id"), uuid(resultSet, "applicant_id"),
                resultSet.getString("application_number"), resultSet.getString("applicant_number"),
                resultSet.getString("applicant_name"), instant(resultSet, "submitted_at"),
                resultSet.getString("application_status"), resultSet.getBigDecimal("calculated_total_points"),
                uuid(resultSet, "intake_id"), resultSet.getString("intake_code"), resultSet.getString("intake_name"),
                uuid(resultSet, "application_type_id"), resultSet.getString("application_type_code"),
                resultSet.getString("application_type_name"), resultSet.getString("applicant_category_code"),
                resultSet.getString("gender_code"), resultSet.getString("disability_status_code"),
                resultSet.getString("sponsor_type_code"), uuid(resultSet, "choice_id"),
                resultSet.getObject("choice_rank", Integer.class), resultSet.getString("choice_status"),
                uuid(resultSet, "programme_id"), resultSet.getString("programme_code"),
                resultSet.getString("programme_name"), resultSet.getString("owning_academic_unit_name"),
                resultSet.getString("qualification_summary"), resultSet.getString("schools_attended"),
                resultSet.getString("decision"), instant(resultSet, "decided_at"), uuid(resultSet, "offer_id"),
                resultSet.getString("offer_number"), resultSet.getString("offer_status"),
                resultSet.getString("offer_type"), instant(resultSet, "portal_published_at"),
                resultSet.getString("email_delivery_status"), resultSet.getString("offer_response"));
    }

    private static AdmissionsIntakeMovementRow mapMovement(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdmissionsIntakeMovementRow(
                uuid(resultSet, "application_id"), resultSet.getString("application_number"),
                resultSet.getString("applicant_number"), resultSet.getString("applicant_name"),
                resultSet.getString("application_status"), uuid(resultSet, "previous_intake_id"),
                resultSet.getString("previous_intake_code"), resultSet.getString("previous_intake_name"),
                uuid(resultSet, "new_intake_id"), resultSet.getString("new_intake_code"),
                resultSet.getString("new_intake_name"), uuid(resultSet, "changed_by_user_id"),
                instant(resultSet, "changed_at"), resultSet.getString("reason"));
    }

    private static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void appendUuid(StringBuilder sql, List<Object> arguments, String column, UUID value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            arguments.add(value);
        }
    }

    private static void appendText(StringBuilder sql, List<Object> arguments, String column, String value) {
        if (value != null) {
            sql.append(" AND UPPER(").append(column).append(") = ?");
            arguments.add(value.toUpperCase(Locale.ROOT));
        }
    }
}
