package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Verifies the governed applicant workspace schema. @author Tinashe K */
@Testcontainers
class ApplicationWorkspaceMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_admissions")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsCurrentAndAuditTablesForEveryWorkspaceRecord() throws SQLException {
        assertCount("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'application_type_sections', 'application_type_sections_aud',
                    'application_sections', 'application_sections_aud',
                    'applicant_next_of_kin', 'applicant_next_of_kin_aud',
                    'applicant_employment_histories', 'applicant_employment_histories_aud',
                    'applicant_referees', 'applicant_referees_aud',
                    'applicant_referee_invitations', 'applicant_referee_invitations_aud')
                """, 12);
    }

    @Test
    void addsServerSubmissionAndQualificationVerificationColumnsToCurrentAndAuditTables() throws SQLException {
        assertCount("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND (
                    (table_name IN ('applications', 'applications_aud') AND column_name IN (
                        'sections_complete', 'declaration_accepted_at',
                        'declaration_accepted_by_user_id', 'declaration_version')) OR
                    (table_name IN ('applicant_qualification_sittings', 'applicant_qualification_sittings_aud')
                        AND column_name IN ('verification_status', 'verified_by_user_id', 'verified_at', 'rejection_reason')))
                """, 16);
    }

    @Test
    void programmeChoiceUniquenessOnlyAppliesToActiveRecords() throws SQLException {
        assertCount("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN ('uk_application_choice_rank', 'uk_application_choice_programme')
                  AND indexdef ILIKE '%WHERE (deleted_at IS NULL)%'
                """, 2);
    }

    @Test
    void consolidatesPersonalDetailsUnderApplicantDetails() throws SQLException {
        assertCount("""
                SELECT count(*)
                FROM application_type_sections
                WHERE section_code = 'PERSONAL_DETAILS'
                  AND section_name = 'Applicant details'
                  AND deleted_at IS NULL
                """, 4);
        assertCount("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '1'
                  AND success
                """, 1);
    }

    @Test
    void protectsActiveRefereeInvitationsAndStoresResponseWorkflowFields() throws SQLException {
        assertCount("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('applicant_referee_invitations', 'applicant_referee_invitations_aud')
                  AND column_name IN (
                      'token_hash', 'status', 'expires_at', 'sent_at', 'opened_at', 'submitted_at',
                      'relationship_to_applicant', 'years_known', 'recommendation', 'comments',
                      'declaration_accepted')
                """, 22);
        assertCount("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uk_active_referee_invitation'
                  AND indexdef ILIKE '%WHERE%deleted_at IS NULL%'
                """, 1);
    }

    @Test
    void seedsInactiveFirstClassRoutesAndCreatesRouteEvidenceAuditTables() throws SQLException {
        assertCount("""
                SELECT count(*) FROM application_types
                WHERE code IN ('UNDERGRAD', 'POSTGRAD', 'MBA', 'EDUCATION')
                  AND is_active = false AND deleted_at IS NULL
                """, 4);
        assertCount("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'application_type_programme_mappings', 'application_type_programme_mappings_aud',
                    'application_programme_option_snapshots', 'application_programme_option_snapshots_aud',
                    'application_document_requirement_snapshots', 'application_document_requirement_snapshots_aud',
                    'application_prior_uz_declarations', 'application_prior_uz_declarations_aud',
                    'application_professional_achievements', 'application_professional_achievements_aud',
                    'application_referee_nominations', 'application_referee_nominations_aud',
                    'admission_qualification_requirement_groups', 'admission_qualification_requirement_groups_aud',
                    'admission_qualification_requirement_items', 'admission_qualification_requirement_items_aud')
                """, 16);
    }

    @Test
    void everyApplicationTypeStartsWithGovernedSectionDefinitions() throws SQLException {
        assertCount("""
                SELECT count(*)
                FROM application_types application_type
                WHERE application_type.deleted_at IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM application_type_sections section_definition
                      WHERE section_definition.application_type_id = application_type.id
                        AND section_definition.deleted_at IS NULL
                        AND section_definition.is_active
                  )
                """, 0);
    }

    @Test
    void makesAcademicSetupIntakesAuthoritativeForApplicationsAndRequirementSets() throws SQLException {
        assertCount("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('applications', 'applications_aud')
                  AND column_name IN (
                      'intake_id', 'intake_code', 'intake_name', 'intake_starts_on',
                      'intake_ends_on', 'maximum_programme_choices')
                """, 12);
        assertCount("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('admission_requirement_sets', 'admission_requirement_sets_aud')
                  AND column_name = 'intake_id'
                """, 2);
        assertCount("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('applications', 'admission_requirement_sets')
                  AND column_name = 'admission_cycle_id'
                  AND is_nullable = 'YES'
                """, 2);
    }

    @Test
    void enforcesLegacyRoundBatchAndReleaseTablesAsReadOnlyHistory() throws SQLException {
        assertCount("""
                SELECT count(*)
                FROM pg_trigger
                WHERE NOT tgisinternal
                  AND tgname = 'trg_protect_legacy_admissions_history'
                  AND tgrelid::regclass::text IN (
                      'selection_rounds', 'selection_decisions', 'offer_batches',
                      'academic_review_assignments', 'academic_unit_recommendations')
                """, 5);
    }

    private void assertCount(String sql, int expected) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(expected, results.getInt(1));
        }
    }
}
