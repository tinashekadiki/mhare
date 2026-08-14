package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineFilterOptions;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReport.FilterOption;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportQuery;

/** Read-only reporting projection over Admissions-owned tables. @author Tinashe K */
@Repository
public class AdmissionsPipelineReportRepository {

    private static final String REPORT_SELECT = """
            SELECT application_record.id AS application_id,
                   applicant.id AS applicant_id,
                   application_record.status AS application_status,
                   application_record.payment_required,
                   application_record.payment_confirmed_at,
                   application_record.payment_override_by_user_id,
                   application_record.intake_id,
                   application_record.intake_code,
                   application_record.intake_name,
                   application_type.id AS application_type_id,
                   application_type.code AS application_type_code,
                   application_type.name AS application_type_name,
                   applicant.applicant_category_code,
                   applicant.gender_code,
                   programme_choice.id AS choice_id,
                   programme_choice.choice_rank,
                   programme_choice.programme_id,
                   programme_choice.programme_code,
                   programme_choice.programme_name,
                   programme_choice.owning_academic_unit_name
              FROM applications application_record
              JOIN applicants applicant
                ON applicant.id = application_record.applicant_id
              JOIN application_types application_type
                ON application_type.id = application_record.application_type_id
              LEFT JOIN application_programme_choices programme_choice
                ON programme_choice.application_id = application_record.id
               AND programme_choice.deleted_at IS NULL
             WHERE application_record.deleted_at IS NULL
            """;

    private static final RowMapper<AdmissionsPipelineReportRow> REPORT_ROW_MAPPER =
            AdmissionsPipelineReportRepository::mapReportRow;

    private final JdbcTemplate jdbcTemplate;

    public AdmissionsPipelineReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionsPipelineReportRow> findReportRows(AdmissionsPipelineReportQuery query) {
        StringBuilder sql = new StringBuilder(REPORT_SELECT);
        List<Object> arguments = new ArrayList<>();
        appendUuidFilter(sql, arguments, "application_record.intake_id", query.intakeId());
        appendUuidFilter(sql, arguments, "application_record.application_type_id", query.applicationTypeId());
        appendTextFilter(sql, arguments, "applicant.applicant_category_code", query.categoryCode());
        appendTextFilter(sql, arguments, "applicant.gender_code", query.genderCode());
        appendUuidFilter(sql, arguments, "programme_choice.programme_id", query.programmeId());
        sql.append(" ORDER BY application_record.id, programme_choice.choice_rank");
        return jdbcTemplate.query(sql.toString(), REPORT_ROW_MAPPER, arguments.toArray());
    }

    public List<AdmissionsDetailedExportRow> findDetailedExportRows(AdmissionsPipelineReportQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT application_record.id AS application_id,
                       application_record.application_number,
                       application_record.submitted_at,
                       application_record.status AS application_status,
                       CASE
                         WHEN NOT application_record.payment_required THEN 'NOT_REQUIRED'
                         WHEN application_record.payment_confirmed_at IS NOT NULL THEN 'PAID'
                         WHEN application_record.payment_override_by_user_id IS NOT NULL THEN 'WAIVED'
                         ELSE 'PENDING'
                       END AS payment_status,
                       application_record.intake_code,
                       application_record.intake_name,
                       application_type.code AS application_type_code,
                       application_type.name AS application_type_name,
                       applicant.applicant_number,
                       TRIM(CONCAT_WS(' ', applicant.first_name,
                            NULLIF(TRIM(applicant.middle_names), ''), applicant.last_name)) AS applicant_name,
                       applicant.primary_email,
                       applicant.primary_phone,
                       applicant.applicant_category_code,
                       applicant.gender_code,
                       application_record.calculated_total_points,
                       COALESCE(STRING_AGG(
                           programme_choice.choice_rank || '. '
                           || COALESCE(programme_choice.programme_code, programme_choice.programme_id::text)
                           || ' - ' || COALESCE(programme_choice.programme_name, 'Unnamed Programme'),
                           ' | ' ORDER BY programme_choice.choice_rank
                       ) FILTER (WHERE programme_choice.id IS NOT NULL), '') AS programme_choices
                  FROM applications application_record
                  JOIN applicants applicant ON applicant.id = application_record.applicant_id
                  JOIN application_types application_type
                    ON application_type.id = application_record.application_type_id
                  LEFT JOIN application_programme_choices programme_choice
                    ON programme_choice.application_id = application_record.id
                   AND programme_choice.deleted_at IS NULL
                 WHERE application_record.deleted_at IS NULL
                """);
        List<Object> arguments = new ArrayList<>();
        appendUuidFilter(sql, arguments, "application_record.intake_id", query.intakeId());
        appendUuidFilter(sql, arguments, "application_record.application_type_id", query.applicationTypeId());
        appendTextFilter(sql, arguments, "applicant.applicant_category_code", query.categoryCode());
        appendTextFilter(sql, arguments, "applicant.gender_code", query.genderCode());
        if (query.programmeId() != null) {
            sql.append("""
                     AND EXISTS (
                         SELECT 1 FROM application_programme_choices selected_choice
                          WHERE selected_choice.application_id = application_record.id
                            AND selected_choice.programme_id = ?
                            AND selected_choice.deleted_at IS NULL
                     )
                    """);
            arguments.add(query.programmeId());
        }
        sql.append("""
                 GROUP BY application_record.id, application_record.application_number,
                          application_record.submitted_at, application_record.status,
                          application_record.payment_required, application_record.payment_confirmed_at,
                          application_record.payment_override_by_user_id, application_record.intake_code,
                          application_record.intake_name, application_type.code, application_type.name,
                          applicant.applicant_number, applicant.first_name, applicant.middle_names,
                          applicant.last_name, applicant.primary_email, applicant.primary_phone,
                          applicant.applicant_category_code, applicant.gender_code,
                          application_record.calculated_total_points
                 ORDER BY applicant.last_name, applicant.first_name, application_record.application_number
                """);
        return jdbcTemplate.query(sql.toString(), AdmissionsPipelineReportRepository::mapDetailedExportRow,
                arguments.toArray());
    }

    public AdmissionsPipelineFilterOptions findFilterOptions() {
        return new AdmissionsPipelineFilterOptions(
                jdbcTemplate.query("""
                        SELECT DISTINCT intake_id::text AS value, intake_code AS code, intake_name AS label
                          FROM applications WHERE deleted_at IS NULL
                         ORDER BY intake_code, intake_name
                        """, AdmissionsPipelineReportRepository::mapFilterOption),
                jdbcTemplate.query("""
                        SELECT DISTINCT application_type.id::text AS value,
                               application_type.code, application_type.name AS label
                          FROM application_types application_type
                          JOIN applications application_record
                            ON application_record.application_type_id = application_type.id
                           AND application_record.deleted_at IS NULL
                         ORDER BY application_type.code, application_type.name
                        """, AdmissionsPipelineReportRepository::mapFilterOption),
                jdbcTemplate.query("""
                        SELECT DISTINCT programme_id::text AS value,
                               COALESCE(programme_code, programme_id::text) AS code,
                               COALESCE(programme_name, programme_code, programme_id::text) AS label
                          FROM application_programme_choices
                         WHERE deleted_at IS NULL
                         ORDER BY code, label
                        """, AdmissionsPipelineReportRepository::mapFilterOption),
                jdbcTemplate.query("""
                        SELECT DISTINCT applicant.applicant_category_code AS value,
                               applicant.applicant_category_code AS code,
                               INITCAP(REPLACE(applicant.applicant_category_code, '_', ' ')) AS label
                          FROM applicants applicant
                          JOIN applications application_record
                            ON application_record.applicant_id = applicant.id
                           AND application_record.deleted_at IS NULL
                         ORDER BY code
                        """, AdmissionsPipelineReportRepository::mapFilterOption),
                jdbcTemplate.query("""
                        SELECT DISTINCT applicant.gender_code AS value,
                               applicant.gender_code AS code,
                               INITCAP(REPLACE(applicant.gender_code, '_', ' ')) AS label
                          FROM applicants applicant
                          JOIN applications application_record
                            ON application_record.applicant_id = applicant.id
                           AND application_record.deleted_at IS NULL
                         WHERE applicant.gender_code IS NOT NULL
                         ORDER BY code
                        """, AdmissionsPipelineReportRepository::mapFilterOption));
    }

    private static void appendUuidFilter(StringBuilder sql, List<Object> arguments, String column, UUID value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            arguments.add(value);
        }
    }

    private static void appendTextFilter(StringBuilder sql, List<Object> arguments, String column, String value) {
        if (value != null) {
            sql.append(" AND UPPER(").append(column).append(") = ?");
            arguments.add(value.toUpperCase(Locale.ROOT));
        }
    }

    private static AdmissionsPipelineReportRow mapReportRow(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp paymentConfirmedAt = resultSet.getTimestamp("payment_confirmed_at");
        return new AdmissionsPipelineReportRow(
                resultSet.getObject("application_id", UUID.class),
                resultSet.getObject("applicant_id", UUID.class),
                resultSet.getString("application_status"),
                resultSet.getBoolean("payment_required"),
                paymentConfirmedAt == null ? null : paymentConfirmedAt.toInstant(),
                resultSet.getObject("payment_override_by_user_id", UUID.class),
                resultSet.getObject("intake_id", UUID.class),
                resultSet.getString("intake_code"),
                resultSet.getString("intake_name"),
                resultSet.getObject("application_type_id", UUID.class),
                resultSet.getString("application_type_code"),
                resultSet.getString("application_type_name"),
                resultSet.getString("applicant_category_code"),
                resultSet.getString("gender_code"),
                resultSet.getObject("choice_id", UUID.class),
                resultSet.getObject("choice_rank", Integer.class),
                resultSet.getObject("programme_id", UUID.class),
                resultSet.getString("programme_code"),
                resultSet.getString("programme_name"),
                resultSet.getString("owning_academic_unit_name"));
    }

    private static AdmissionsDetailedExportRow mapDetailedExportRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Timestamp submittedAt = resultSet.getTimestamp("submitted_at");
        return new AdmissionsDetailedExportRow(
                resultSet.getObject("application_id", UUID.class),
                resultSet.getString("application_number"),
                submittedAt == null ? null : submittedAt.toInstant(),
                resultSet.getString("application_status"),
                resultSet.getString("payment_status"),
                resultSet.getString("intake_code"),
                resultSet.getString("intake_name"),
                resultSet.getString("application_type_code"),
                resultSet.getString("application_type_name"),
                resultSet.getString("applicant_number"),
                resultSet.getString("applicant_name"),
                resultSet.getString("primary_email"),
                resultSet.getString("primary_phone"),
                resultSet.getString("applicant_category_code"),
                resultSet.getString("gender_code"),
                resultSet.getBigDecimal("calculated_total_points"),
                resultSet.getString("programme_choices"));
    }

    private static FilterOption mapFilterOption(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FilterOption(
                resultSet.getString("value"), resultSet.getString("code"), resultSet.getString("label"));
    }
}
