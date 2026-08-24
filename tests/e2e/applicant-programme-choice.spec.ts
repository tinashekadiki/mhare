import { expect, request as playwrightRequest, test, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const applicantPortalUrl = process.env.APPLICANT_PORTAL_URL ?? "http://localhost:3001";
const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? "http://localhost:8099";
const keycloakRealm = process.env.KEYCLOAK_REALM ?? "emhare";
const postgresContainer = process.env.POSTGRES_CONTAINER ?? "emhare-postgres";
const testPassword = "Temporary-Applicant-UI-Password-42";

type ApplicantFixture = {
  userId: string;
  username: string;
  academicUnitTypeRootId: string;
  academicUnitTypeLeafId: string;
  academicUnitRootId: string;
  academicUnitLeafId: string;
  academicYearId: string;
  intakeId: string;
  programmeLevelId: string;
  programmeTypeId: string;
  programmeId: string;
  programmeVersionId: string;
  moduleId: string;
  curriculumModuleId: string;
  examBodyId: string;
  subjectId: string;
  secondSubjectId: string;
  applicationTypeId: string;
  feeStructureId: string;
  feeCatalogueId: string;
  feeRuleId: string;
  countryId: string;
  calendarYear: number;
  codeSuffix: string;
  programmeCode: string;
  intakeName: string;
  applicationTypeName: string;
  firstRefereeEmail: string;
  secondRefereeEmail: string;
  thirdRefereeEmail: string;
};

type ApplicantFixtureOptions = {
  route?: "UNDERGRAD" | "POSTGRAD" | "MBA" | "EDUCATION";
};

type ApplicantLoginFixture = Pick<ApplicantFixture, "userId" | "username">;

function executeSql(database: string, sql: string, tuplesOnly = false) {
  const args = [
    "exec",
    "-i",
    postgresContainer,
    "psql",
    "-q",
    "-v",
    "ON_ERROR_STOP=1",
    "-U",
    "postgres",
    "-d",
    database,
  ];
  if (tuplesOnly) args.push("-A", "-t");
  const result = spawnSync("docker", args, { input: sql, encoding: "utf8" });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout;
}

async function keycloakAdminContext() {
  const tokenContext = await playwrightRequest.newContext();
  const tokenResponse = await tokenContext.post(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        client_id: "admin-cli",
        username: "admin",
        password: "admin",
        grant_type: "password",
      },
    },
  );
  expect(tokenResponse.ok()).toBeTruthy();
  const token = (await tokenResponse.json()).access_token;
  await tokenContext.dispose();
  return playwrightRequest.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });
}

async function createApplicantLoginFixture(): Promise<ApplicantLoginFixture> {
  const runId = randomUUID();
  const username = `applicant-route-ui-${runId}@example.test`;
  const keycloak = await keycloakAdminContext();
  const createUser = await keycloak.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username,
      email: username,
      firstName: "Route",
      lastName: "Applicant",
      enabled: true,
      emailVerified: true,
      credentials: [{ type: "password", value: testPassword, temporary: false }],
    },
  });
  expect(createUser.status()).toBe(201);
  const userId = createUser.headers().location!.split("/").at(-1)!;
  const applicantRole = await keycloak.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/applicant`,
  );
  await keycloak.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    {
      data: [await applicantRole.json()],
    },
  );
  await keycloak.dispose();
  return { userId, username };
}

async function cleanupApplicantLoginFixture(fixture: ApplicantLoginFixture | null) {
  if (!fixture) return;
  const coreUserId = executeSql(
    "emhare_core_identity",
    `SELECT id FROM users WHERE email = '${fixture.username}';`,
    true,
  ).trim();
  if (coreUserId) {
    const applicationCount = Number(
      executeSql(
        "emhare_admissions",
        `
SELECT COUNT(*) FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
`,
        true,
      ).trim() || "0",
    );
    if (applicationCount === 0) {
      executeSql(
        "emhare_admissions",
        `
DELETE FROM applicants_aud WHERE user_id = '${coreUserId}';
DELETE FROM applicants WHERE user_id = '${coreUserId}';
`,
      );
      executeSql(
        "emhare_core_identity",
        `
DELETE FROM user_role_assignments_aud WHERE user_id = '${coreUserId}'; DELETE FROM user_role_assignments WHERE user_id = '${coreUserId}';
DELETE FROM login_events_aud WHERE user_id = '${coreUserId}'; DELETE FROM login_events WHERE user_id = '${coreUserId}';
DELETE FROM users_aud WHERE id = '${coreUserId}'; DELETE FROM users WHERE id = '${coreUserId}';
`,
      );
    }
  }
  const keycloak = await keycloakAdminContext();
  await keycloak.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`);
  await keycloak.dispose();
}

async function createFixture(options: ApplicantFixtureOptions = {}): Promise<ApplicantFixture> {
  const admissionRoute = options.route ?? "UNDERGRAD";
  const isPostgraduateProgramme = admissionRoute !== "UNDERGRAD";
  const requiresEmploymentHistory = admissionRoute !== "UNDERGRAD";
  const refereeMinimumRecords =
    admissionRoute === "POSTGRAD" ? 2 : admissionRoute === "UNDERGRAD" ? 0 : 3;
  const requiresMbaDeclarations = admissionRoute === "MBA";
  const programmeName =
    admissionRoute === "MBA"
      ? "Master of Business Administration"
      : admissionRoute === "POSTGRAD"
        ? "Master of Data Science"
        : admissionRoute === "EDUCATION"
          ? "Master of Education"
          : "Browser Verified Programme";
  const applicationTypeName =
    admissionRoute === "MBA"
      ? "Master of Business Administration"
      : admissionRoute === "POSTGRAD"
        ? "Postgraduate"
        : admissionRoute === "EDUCATION"
          ? "Education"
          : "Undergraduate";
  const programmeCodePrefix =
    admissionRoute === "MBA"
      ? "MBA"
      : admissionRoute === "POSTGRAD"
        ? "PG"
        : admissionRoute === "EDUCATION"
          ? "ED"
          : "UG";
  const runId = randomUUID();
  const codeSuffix = runId.replaceAll("-", "").slice(0, 8).toUpperCase();
  const username = `applicant-programme-${runId}@example.test`;
  const keycloak = await keycloakAdminContext();
  const createUser = await keycloak.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username,
      email: username,
      firstName: "Browser",
      lastName: "Applicant",
      enabled: true,
      emailVerified: true,
      credentials: [{ type: "password", value: testPassword, temporary: false }],
    },
  });
  expect(createUser.status()).toBe(201);
  const userId = createUser.headers().location!.split("/").at(-1)!;
  const applicantRole = await keycloak.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/applicant`,
  );
  await keycloak.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    {
      data: [await applicantRole.json()],
    },
  );
  await keycloak.dispose();

  const academicUnitTypeRootId = executeSql(
    "emhare_academic_setup",
    "SELECT id FROM academic_unit_types WHERE code = 'FACULTY' AND deleted_at IS NULL;",
    true,
  ).trim();
  const academicUnitTypeLeafId = executeSql(
    "emhare_academic_setup",
    "SELECT id FROM academic_unit_types WHERE code = 'DEPARTMENT' AND deleted_at IS NULL;",
    true,
  ).trim();
  if (!academicUnitTypeRootId || !academicUnitTypeLeafId)
    throw new Error(
      "Canonical FACULTY and DEPARTMENT academic unit types are required for the browser fixture.",
    );
  const calendarYear = 6000 + (Number.parseInt(codeSuffix.slice(0, 4), 16) % 3000);
  const fixture: ApplicantFixture = {
    userId,
    username,
    academicUnitTypeRootId,
    academicUnitTypeLeafId,
    academicUnitRootId: randomUUID(),
    academicUnitLeafId: randomUUID(),
    academicYearId: executeSql(
      "emhare_academic_setup",
      "SELECT id FROM academic_years WHERE status = 'OPEN' AND CURRENT_DATE BETWEEN start_date AND end_date AND deleted_at IS NULL ORDER BY start_date DESC LIMIT 1;",
      true,
    ).trim(),
    intakeId: randomUUID(),
    programmeLevelId: randomUUID(),
    programmeTypeId: randomUUID(),
    programmeId: randomUUID(),
    programmeVersionId: randomUUID(),
    moduleId: randomUUID(),
    curriculumModuleId: randomUUID(),
    examBodyId: randomUUID(),
    subjectId: randomUUID(),
    secondSubjectId: randomUUID(),
    applicationTypeId: randomUUID(),
    feeStructureId: randomUUID(),
    feeCatalogueId: randomUUID(),
    feeRuleId: randomUUID(),
    countryId: executeSql(
      "emhare_core_identity",
      "SELECT id FROM countries WHERE iso2_code = 'ZW' AND deleted_at IS NULL;",
      true,
    ).trim(),
    calendarYear,
    codeSuffix,
    programmeCode: `${programmeCodePrefix}${codeSuffix.slice(0, 5 - programmeCodePrefix.length)}`,
    intakeName: `Browser E2E Intake ${codeSuffix}`,
    applicationTypeName: `${applicationTypeName} ${codeSuffix}`,
    firstRefereeEmail: `referee-one-${codeSuffix.toLowerCase()}@example.test`,
    secondRefereeEmail: `referee-two-${codeSuffix.toLowerCase()}@example.test`,
    thirdRefereeEmail: `referee-three-${codeSuffix.toLowerCase()}@example.test`,
  };
  const year = calendarYear;
  const uniqueSortOrder = Number.parseInt(codeSuffix.slice(0, 7), 16);
  executeSql(
    "emhare_academic_setup",
    `
BEGIN;
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, version) VALUES
('${fixture.academicUnitRootId}', '${fixture.academicUnitTypeRootId}', null, 'BSI_${codeSuffix}', 'Faculty of Science', 'ACTIVE', now(), now(), 0),
('${fixture.academicUnitLeafId}', '${fixture.academicUnitTypeLeafId}', '${fixture.academicUnitRootId}', 'BCO_${codeSuffix}', 'Department of Computing', 'ACTIVE', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, maximum_programme_choices, created_at, updated_at, version)
VALUES ('${fixture.intakeId}', '${fixture.academicYearId}', 'BI_${codeSuffix}', '${fixture.intakeName}', CURRENT_DATE - 1, CURRENT_DATE + 30, 'DRAFT', 3, now(), now(), 0);
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
VALUES ('${fixture.programmeLevelId}', '${isPostgraduateProgramme ? "BPG" : "BUG"}_${codeSuffix}', '${isPostgraduateProgramme ? "Postgraduate" : "Undergraduate"}', ${uniqueSortOrder}, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES ('${fixture.programmeTypeId}', '${admissionRoute}_${codeSuffix}', '${programmeName}', 'ACTIVE', now(), now(), 0);
INSERT INTO intake_programme_level_targets (id, intake_id, programme_level_id, created_at, updated_at, version)
VALUES (gen_random_uuid(), '${fixture.intakeId}', '${fixture.programmeLevelId}', now(), now(), 0);
UPDATE intakes SET status = 'OPEN', updated_at = now(), version = 1 WHERE id = '${fixture.intakeId}';
INSERT INTO modules (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, version)
VALUES ('${fixture.moduleId}', '${fixture.academicUnitLeafId}', 'BCS_${codeSuffix}', 'Programming Fundamentals', 'Browser fixture Module.', 12.00, 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES ('${fixture.programmeId}', '${fixture.academicUnitLeafId}', '${fixture.programmeTypeId}', '${fixture.programmeLevelId}', '${fixture.programmeCode}', '${programmeName}', '${isPostgraduateProgramme ? programmeName : "Bachelor of Science Honours Degree"}', ${isPostgraduateProgramme ? 4 : 8}, ${isPostgraduateProgramme ? 6 : 12}, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, created_at, updated_at, version)
VALUES ('${fixture.programmeVersionId}', '${fixture.programmeId}', '${year}.1', CURRENT_DATE - 1, 'DRAFT', now(), now(), 0);
INSERT INTO curriculum_modules (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, version)
VALUES ('${fixture.curriculumModuleId}', '${fixture.programmeVersionId}', '${fixture.moduleId}', 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), 0);
UPDATE programme_versions SET status = 'APPROVED', approved_by_user_id = '${userId}', approved_at = now(), version = 1 WHERE id = '${fixture.programmeVersionId}';
COMMIT;
`,
  );
  executeSql(
    "emhare_finance",
    `
BEGIN;
INSERT INTO finance_fee_catalogues (
  id, code, name, description, charge_type, receivable_account_code,
  revenue_account_code, base_currency_code, status, prepared_by_user_id,
  activated_by_user_id, activated_at, activation_reason, created_at, updated_at, version)
VALUES (
  '${fixture.feeCatalogueId}', 'APP-${codeSuffix}', 'Application fee ${codeSuffix}',
  'Playwright-governed application fee.', 'APPLICATION', 'AR-APPLICATION',
  'REV-APPLICATION', 'USD', 'ACTIVE', '${userId}', gen_random_uuid(), now(),
  'Independent Finance activation for browser verification.', now(), now(), 0);
INSERT INTO finance_fee_structures (
  id, code, name, description, fee_context, scope_type, scope_reference_id,
  scope_reference_code, scope_reference_name, programme_level_id,
  programme_level_code, programme_level_name, transaction_currency_code,
  effective_from, status, prepared_by_user_id, activated_by_user_id,
  activated_at, activation_reason, created_at, updated_at, version)
VALUES (
  '${fixture.feeStructureId}', 'APP-STRUCT-${codeSuffix}', 'Application fee structure ${codeSuffix}',
  'Playwright-governed application fee structure.', 'APPLICATION', 'PROGRAMME_LEVEL',
  '${fixture.programmeLevelId}', '${isPostgraduateProgramme ? "BPG" : "BUG"}_${codeSuffix}',
  '${isPostgraduateProgramme ? "Postgraduate" : "Undergraduate"}', '${fixture.programmeLevelId}',
  '${isPostgraduateProgramme ? "BPG" : "BUG"}_${codeSuffix}',
  '${isPostgraduateProgramme ? "Postgraduate" : "Undergraduate"}', 'USD',
  now() - interval '1 day', 'ACTIVE', '${userId}', gen_random_uuid(), now(),
  'Independent Finance activation for browser verification.', now(), now(), 0);
INSERT INTO finance_fee_rules (
  id, fee_catalogue_id, fee_structure_id, structure_line_number,
  structure_line_description, rule_version, transaction_currency_code,
  transaction_amount, base_currency_code, base_amount, rating_status,
  effective_from, status, prepared_by_user_id, created_at, updated_at, version)
VALUES (
  '${fixture.feeRuleId}', '${fixture.feeCatalogueId}', '${fixture.feeStructureId}', 1,
  'Application processing fee', 1, 'USD', 25.00, 'USD', 25.00, 'RATED',
  now() - interval '1 day', 'DRAFT', '${userId}', now(), now(), 0);
INSERT INTO finance_fee_rule_scopes (
  id, fee_rule_id, scope_dimension, reference_id, reference_code, reference_name,
  created_at, updated_at, version)
VALUES (
  gen_random_uuid(), '${fixture.feeRuleId}', 'PROGRAMME_LEVEL', '${fixture.programmeLevelId}',
  '${isPostgraduateProgramme ? "BPG" : "BUG"}_${codeSuffix}',
  '${isPostgraduateProgramme ? "Postgraduate" : "Undergraduate"}', now(), now(), 0);
UPDATE finance_fee_rules
SET status = 'APPROVED', approved_by_user_id = gen_random_uuid(), approved_at = now(),
    approval_reason = 'Independent Finance approval for browser verification.', version = 1
WHERE id = '${fixture.feeRuleId}';
COMMIT;
`,
  );
  executeSql(
    "emhare_admissions",
    `
INSERT INTO application_types (
  id, code, name, requires_employment_history, requires_referees, is_active,
  finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name,
  fee_policy_status, fee_policy_decided_by_user_id, fee_policy_decided_at,
  created_at, updated_at, version)
VALUES (
  '${fixture.applicationTypeId}', '${admissionRoute}-E2E-${runId.slice(0, 8)}',
  '${fixture.applicationTypeName}', ${requiresEmploymentHistory}, ${refereeMinimumRecords > 0}, true,
  '${fixture.feeStructureId}', 'APP-STRUCT-${codeSuffix}', 'Application fee structure ${codeSuffix}',
  'FEE_STRUCTURE', '${userId}', now(), now(), now(), 0);
INSERT INTO application_type_programme_mappings (
  id, application_type_id, programme_id, programme_code, programme_name, is_active,
  created_at, updated_at, version)
VALUES (gen_random_uuid(), '${fixture.applicationTypeId}', '${fixture.programmeId}', '${fixture.programmeCode}',
  '${programmeName}', true,
  now(), now(), 0);
INSERT INTO application_type_sections (
  id, application_type_id, section_code, section_name, is_required, is_repeatable,
  minimum_records, sort_order, is_active, created_at, updated_at, version)
VALUES
(gen_random_uuid(), '${fixture.applicationTypeId}', 'PERSONAL_DETAILS', 'Applicant details', true, false, 0, 10, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30, true, now(), now(), 0),
${requiresEmploymentHistory ? `(gen_random_uuid(), '${fixture.applicationTypeId}', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40, true, now(), now(), 0),` : ""}
${
  requiresMbaDeclarations
    ? `(gen_random_uuid(), '${fixture.applicationTypeId}', 'PRIOR_UZ_STUDY', 'Previous UZ study', true, false, 0, 42, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'PROFESSIONAL_ACHIEVEMENTS', 'Professional achievements', true, true, 0, 44, true, now(), now(), 0),`
    : ""
}
${refereeMinimumRecords > 0 ? `(gen_random_uuid(), '${fixture.applicationTypeId}', 'REFEREES', 'Referees', true, true, ${refereeMinimumRecords}, 50, true, now(), now(), 0),` : ""}
(gen_random_uuid(), '${fixture.applicationTypeId}', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90, true, now(), now(), 0);
INSERT INTO application_type_document_requirements (id, application_type_id, requirement_code, requirement_name, is_required, capture_section_code, sort_order, is_active, created_at, updated_at, version)
VALUES
(gen_random_uuid(), '${fixture.applicationTypeId}', 'NATIONAL_ID', 'National ID', true, 'PERSONAL_DETAILS', 10, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'BIRTH_CERTIFICATE', 'Birth Certificate', true, 'PERSONAL_DETAILS', 20, true, now(), now(), 0);
INSERT INTO exam_bodies (id, code, name, country_id, is_active, created_at, updated_at, version)
VALUES ('${fixture.examBodyId}', 'ZIMSEC_${codeSuffix}', 'Zimbabwe School Examinations Council ${codeSuffix}', '${fixture.countryId}', true, now(), now(), 0);
INSERT INTO admission_subjects (id, code, name, level, subject_group_code, is_active, created_at, updated_at, version)
VALUES
('${fixture.subjectId}', 'ENG_${codeSuffix}', 'English Language ${codeSuffix}', 'O_LEVEL', 'LANGUAGE', true, now(), now(), 0),
('${fixture.secondSubjectId}', 'MATH_${codeSuffix}', 'Mathematics ${codeSuffix}', 'O_LEVEL', 'SCIENCE', true, now(), now(), 0);
`,
  );
  return fixture;
}

async function cleanupFixture(fixture: ApplicantFixture | null) {
  if (!fixture) return;
  const coreUserId = executeSql(
    "emhare_core_identity",
    `SELECT id FROM users WHERE email = '${fixture.username}';`,
    true,
  ).trim();
  if (coreUserId) {
    executeSql(
      "emhare_admissions",
      `
DELETE FROM applicant_qualification_results_aud WHERE qualification_sitting_id IN (SELECT id FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM applicant_qualification_results WHERE qualification_sitting_id IN (SELECT id FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM applicant_qualification_sittings_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_next_of_kin_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_next_of_kin WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_employment_histories_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_employment_histories WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_referee_invitations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_referee_invitations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_referee_nominations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_referee_nominations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_referees_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_referees WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM application_prior_uz_declarations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_prior_uz_declarations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_professional_achievements_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_professional_achievements WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_payment_references_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_payment_references WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_documents_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_documents WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_document_requirement_snapshots_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_document_requirement_snapshots WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_option_snapshots_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_option_snapshots WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_accommodation_requests_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_accommodation_requests WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_exam_arrangements_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_exam_arrangements WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_evaluations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_evaluations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_sections_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_sections WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_status_events_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_status_events WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_entry_option_selections_aud WHERE programme_choice_id IN (SELECT id FROM application_programme_choices WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM application_programme_entry_option_selections WHERE programme_choice_id IN (SELECT id FROM application_programme_choices WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM application_programme_choices_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_choices WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applications_aud WHERE id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicants_aud WHERE user_id = '${coreUserId}';
DELETE FROM applicants WHERE user_id = '${coreUserId}';
`,
    );
    executeSql(
      "emhare_core_identity",
      `
DELETE FROM user_role_assignments_aud WHERE user_id = '${coreUserId}'; DELETE FROM user_role_assignments WHERE user_id = '${coreUserId}';
DELETE FROM login_events_aud WHERE user_id = '${coreUserId}'; DELETE FROM login_events WHERE user_id = '${coreUserId}';
DELETE FROM users_aud WHERE id = '${coreUserId}'; DELETE FROM users WHERE id = '${coreUserId}';
`,
    );
  }
  executeSql(
    "emhare_admissions",
    `
DELETE FROM application_type_sections_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_sections WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_programme_mappings_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_programme_mappings WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_document_requirements_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_document_requirements WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_fees_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_fees WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM admission_cycles_aud WHERE id IN (SELECT id FROM admission_cycles WHERE intake_id = '${fixture.intakeId}');
DELETE FROM admission_cycles WHERE intake_id = '${fixture.intakeId}';
DELETE FROM application_types WHERE id = '${fixture.applicationTypeId}';
DELETE FROM admission_subjects WHERE id IN ('${fixture.subjectId}', '${fixture.secondSubjectId}');
DELETE FROM exam_bodies WHERE id = '${fixture.examBodyId}';
`,
  );
  executeSql(
    "emhare_finance",
    `
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM finance_fee_rule_scopes_aud WHERE fee_rule_id = '${fixture.feeRuleId}';
DELETE FROM finance_fee_rule_scopes WHERE fee_rule_id = '${fixture.feeRuleId}';
DELETE FROM finance_fee_rules_aud WHERE id = '${fixture.feeRuleId}';
DELETE FROM finance_fee_rules WHERE id = '${fixture.feeRuleId}';
DELETE FROM finance_fee_structures_aud WHERE id = '${fixture.feeStructureId}';
DELETE FROM finance_fee_structures WHERE id = '${fixture.feeStructureId}';
DELETE FROM finance_fee_catalogues_aud WHERE id = '${fixture.feeCatalogueId}';
DELETE FROM finance_fee_catalogues WHERE id = '${fixture.feeCatalogueId}';
COMMIT;
`,
  );
  executeSql(
    "emhare_academic_setup",
    `
BEGIN; SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules WHERE id = '${fixture.curriculumModuleId}'; DELETE FROM programme_versions WHERE id = '${fixture.programmeVersionId}';
DELETE FROM programmes WHERE id = '${fixture.programmeId}'; DELETE FROM modules WHERE id = '${fixture.moduleId}';
DELETE FROM intake_programme_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM intake_programme_level_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM programme_types WHERE id = '${fixture.programmeTypeId}'; DELETE FROM programme_levels WHERE id = '${fixture.programmeLevelId}';
DELETE FROM intakes WHERE id = '${fixture.intakeId}';
DELETE FROM academic_units WHERE id = '${fixture.academicUnitLeafId}'; DELETE FROM academic_units WHERE id = '${fixture.academicUnitRootId}'; COMMIT;
`,
  );
  const keycloak = await keycloakAdminContext();
  await keycloak.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`);
  await keycloak.dispose();
}

async function login(page: Page, fixture: ApplicantLoginFixture) {
  await page.getByRole("button", { name: "Sign in" }).first().click();
  await page.locator("#username").fill(fixture.username);
  await page.locator("#password").fill(testPassword);
  await page.locator("#kc-login").click();
  await page.waitForURL(`${applicantPortalUrl}/**`, { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

async function selectOption(page: Page, label: string | RegExp, option: string | RegExp) {
  const field = page.getByLabel(label).first();
  await expect(field).toBeEnabled({ timeout: 30_000 });
  let lastError: unknown;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await field.scrollIntoViewIfNeeded().catch(() => undefined);
    await field.evaluate((element: HTMLElement) => element.click());
    const optionLocator = page
      .getByRole("option", { name: option, exact: typeof option === "string" })
      .first();
    try {
      await optionLocator.waitFor({ state: "visible", timeout: 10_000 });
      const optionText = (await optionLocator.textContent())?.trim();
      if (!optionText) throw new Error(`Option text was empty for ${String(option)}.`);
      await page.keyboard.type(optionText);
      await page.keyboard.press("Enter");
      await expect(field).toContainText(option, { timeout: 5_000 });
      await page.keyboard.press("Escape");
      await expect(page.getByRole("listbox"))
        .toBeHidden({ timeout: 5_000 })
        .catch(() => undefined);
      return;
    } catch (error) {
      lastError = error;
      await page.keyboard.press("Escape");
      await page.waitForLoadState("networkidle").catch(() => undefined);
      await page.waitForTimeout(500);
    }
  }
  throw lastError;
}

async function selectApplicationRoute(page: Page, applicationTypeName: string) {
  const routeCard = page.getByRole("button", {
    name: new RegExp(applicationTypeName),
  });
  await expect(routeCard).toBeEnabled({ timeout: 30_000 });
  await routeCard.evaluate((element: HTMLElement) => element.click());
  await expect(routeCard).toHaveAttribute("aria-pressed", "true");
}

async function selectOptionUntilFieldContains(
  page: Page,
  label: string,
  option: string | RegExp,
  expectedText: string,
) {
  const field = page.getByLabel(label).first();
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await selectOption(page, label, option);
    if (((await field.textContent()) ?? "").includes(expectedText)) return;
  }
  await expect(field).toContainText(expectedText);
}

async function clickVisibleButtonContaining(page: Page, label: string) {
  await page.waitForFunction((buttonLabel) => {
    return Array.from(document.querySelectorAll("button")).some(
      (button) =>
        !button.disabled &&
        button.offsetParent !== null &&
        (button.textContent ?? "").replace(/\s+/g, " ").includes(buttonLabel),
    );
  }, label);
  await page.evaluate((buttonLabel) => {
    const buttons = Array.from(document.querySelectorAll("button")).filter(
      (candidate) =>
        !candidate.disabled &&
        candidate.offsetParent !== null &&
        (candidate.textContent ?? "").replace(/\s+/g, " ").includes(buttonLabel),
    );
    const button = buttons.at(-1);
    if (!(button instanceof HTMLButtonElement))
      throw new Error(`Button was not found: ${buttonLabel}`);
    button.click();
  }, label);
}

async function dismissSuccessDialog(page: Page) {
  await expect(page.locator(".swal2-icon.swal2-success")).toBeVisible();
  const confirmation = page.locator(".swal2-confirm").filter({ hasText: "OK" }).last();
  await confirmation.waitFor({ state: "visible" });
  await confirmation.evaluate((button: HTMLElement) => button.click());
  await expect(page.locator(".swal2-container")).toBeHidden({
    timeout: 15_000,
  });
}

async function uploadAutomaticEvidence(page: Page, requirementName: string, filePath: string) {
  const evidenceCard = page
    .getByRole("heading", { name: new RegExp(`^${requirementName}(?: \\*)?$`) })
    .locator("xpath=ancestor::section[1]");
  const fileChooserPromise = page.waitForEvent("filechooser");
  await evidenceCard
    .getByRole("button", { name: /Drop (?:the document here|a replacement) or click to choose/ })
    .click();
  const fileChooser = await fileChooserPromise;
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().includes("/api/documents/uploads") &&
        response.request().method() === "POST" &&
        response.ok(),
    ),
    fileChooser.setFiles(filePath),
  ]);
  await expect(evidenceCard.getByText(/Ready to review|Uploaded/).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function uploadRequiredPersonalEvidence(
  page: Page,
  testInfo: { outputPath: (...pathSegments: string[]) => string },
  codeSuffix: string,
) {
  const evidencePath = testInfo.outputPath(`identity-evidence-${codeSuffix}.pdf`);
  mkdirSync(dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `%PDF-1.4\n% redacted identity evidence ${codeSuffix}\n%%EOF\n`);
  await uploadAutomaticEvidence(page, "National ID", evidencePath);
  await uploadAutomaticEvidence(page, "Birth Certificate", evidencePath);
  await expect(page.getByText("Upload the required identity evidence to continue")).toHaveCount(0);
  await expect(page.getByLabel("Title")).toBeEnabled();
}

async function completeSchoolQualification(
  page: Page,
  testInfo: { outputPath: (...pathSegments: string[]) => string },
  fixture: ApplicantFixture,
  institutionName: string,
  centreNumber: string,
  candidateNumber: string,
) {
  const evidencePath = testInfo.outputPath(`qualification-evidence-${fixture.codeSuffix}.pdf`);
  mkdirSync(dirname(evidencePath), { recursive: true });
  writeFileSync(
    evidencePath,
    `%PDF-1.4\n% redacted qualification evidence ${fixture.codeSuffix}\n%%EOF\n`,
  );
  await uploadAutomaticEvidence(page, "Qualification evidence", evidencePath);
  await selectOption(page, "Exam body", new RegExp(`ZIMSEC_${fixture.codeSuffix}`));
  await page.getByLabel("School or institution").fill(institutionName);
  await page.getByLabel("Year written").fill("2024");
  await page.getByLabel("Centre number").fill(centreNumber);
  await page.getByLabel("Candidate number").fill(candidateNumber);
  await selectOption(page, "Country", /ZW .*Zimbabwe/);

  while ((await page.getByRole("button", { name: /Remove subject/ }).count()) > 2) {
    await page
      .getByRole("button", { name: /Remove subject/ })
      .last()
      .click();
  }
  await page
    .getByLabel("Managed subject")
    .first()
    .evaluate((element: HTMLElement) => element.click());
  await page
    .getByRole("option", { name: new RegExp(`ENG_${fixture.codeSuffix}`) })
    .click({ force: true });
  await page
    .getByLabel("Grade")
    .first()
    .evaluate((element: HTMLElement) => element.click());
  await page.getByRole("option", { name: "A", exact: true }).last().click({ force: true });
  await page
    .getByLabel("Managed subject")
    .nth(1)
    .evaluate((element: HTMLElement) => element.click());
  await page
    .getByRole("option", { name: new RegExp(`MATH_${fixture.codeSuffix}`) })
    .click({ force: true });
  await page
    .getByLabel("Grade")
    .nth(1)
    .evaluate((element: HTMLElement) => element.click());
  await page.getByRole("option", { name: "B", exact: true }).last().click({ force: true });
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.url().endsWith("/qualification-aggregates") &&
        response.request().method() === "POST" &&
        response.ok(),
    ),
    clickVisibleButtonContaining(page, "Save record"),
  ]);
  await expect(page.getByText(`English Language ${fixture.codeSuffix}`)).toBeVisible();
  await expect(page.getByText(`Mathematics ${fixture.codeSuffix}`)).toBeVisible();
}

async function waitForReferenceInvitation(email: string) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const serializedNotification = executeSql(
      "emhare_notifications",
      `
SELECT json_build_object('status', status, 'providerCode', provider_code, 'body', body)::text
FROM notification_requests
WHERE recipient_address = '${email}'
  AND template_code = 'REFEREE_REFERENCE_REQUEST_EMAIL'
ORDER BY created_at DESC
LIMIT 1;
`,
      true,
    ).trim();
    if (serializedNotification) {
      const notification = JSON.parse(serializedNotification) as {
        status: string;
        providerCode: string | null;
        body: string;
      };
      if (notification.status === "SENT") {
        const responseUrl = notification.body.match(
          /https?:\/\/[^\s<]+\/references\/[A-Za-z0-9_-]+/,
        )?.[0];
        if (!responseUrl) throw new Error(`Reference response URL was not rendered for ${email}.`);
        return { ...notification, responseUrl };
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Reference invitation was not delivered to ${email} within 30 seconds.`);
}

async function submitConfidentialReference(page: Page, responseUrl: string, relationship: string) {
  await page.goto(responseUrl);
  await page.waitForLoadState("networkidle");
  await expect(
    page.getByRole("heading", { name: "Confidential reference", exact: true }),
  ).toBeVisible({ timeout: 15_000 });
  await page.getByLabel("Relationship to applicant").fill(relationship);
  await page.getByLabel("Years known").fill("5");
  await selectOption(page, "Recommendation", "Strongly recommend");
  await page
    .getByLabel("Reference comments")
    .fill(
      "The applicant demonstrates sound judgement, leadership, academic readiness, and strong potential for MBA study.",
    );
  await page
    .getByLabel(
      "I confirm that this confidential reference is accurate and represents my own assessment.",
    )
    .evaluate((element: HTMLElement) => element.click());
  await clickVisibleButtonContaining(page, "Submit confidential reference");
  await expect(
    page.getByRole("heading", { name: "Reference submitted", exact: true }),
  ).toBeVisible();
}

test.describe("Applicant programme choices", () => {
  for (const evidenceScenario of [
    {
      categoryCode: "LOCAL",
      label: "Local",
      requirements: [
        ["NATIONAL_ID", "National ID"],
        ["BIRTH_CERTIFICATE", "Birth Certificate"],
      ],
    },
    {
      categoryCode: "INTERNATIONAL",
      label: "International",
      requirements: [["PASSPORT", "Passport"]],
    },
  ] as const) {
    test(`${evidenceScenario.label} personal details unlock only after applicable evidence auto-uploads`, async ({
      page,
    }, testInfo) => {
      test.setTimeout(90_000);
      let fixture: ApplicantLoginFixture | null = null;
      try {
        fixture = await createApplicantLoginFixture();
        await page.goto(applicantPortalUrl);
        await login(page, fixture);

        const applicationId = randomUUID();
        const uploadedRequirementCodes = new Set<string>();
        const requirementDocumentIds = new Map<string, string>();
        const workspace = () => ({
          application: {
            id: applicationId,
            applicationNumber: `EMH-EVIDENCE-${evidenceScenario.categoryCode}`,
            applicantNumber: "A-EVIDENCE-001",
            applicantName: "Route Applicant",
            intakeId: randomUUID(),
            intakeCode: "AUG-2027",
            applicationTypeId: randomUUID(),
            applicationTypeName: "Undergraduate and Diploma",
            status: "DRAFT",
            paymentRequired: false,
            paymentClearanceStatus: "NOT_REQUIRED",
            paymentWaiverReason: null,
            canSubmit: false,
            canEnterReview: false,
            calculatedTotalPoints: null,
            pointsCalculatedAt: null,
            programmeChoices: [],
            payment: null,
          },
          profile: {
            id: randomUUID(),
            userId: fixture!.userId,
            applicantNumber: "A-EVIDENCE-001",
            applicantCategoryCode: evidenceScenario.categoryCode,
            titleCode: null,
            firstName: "Route",
            middleNames: null,
            lastName: "Applicant",
            dateOfBirth: null,
            genderCode: null,
            maritalStatusCode: null,
            nationalIdNumber: null,
            passportNumber: null,
            countryId: null,
            nationalityCountryId: null,
            placeOfBirth: null,
            disabilityStatusCode: null,
            specialNeeds: null,
            sponsorTypeCode: null,
            primaryEmail: fixture!.username,
            primaryPhone: null,
            postalAddress: null,
            residentialAddress: null,
            completenessPercentage: 20,
            missingRequiredFields: ["dateOfBirth"],
            createdAt: "2026-08-23T10:00:00Z",
            updatedAt: "2026-08-23T10:00:00Z",
            version: 0,
          },
          sections: [
            {
              id: randomUUID(),
              code: "PERSONAL_DETAILS",
              name: "Personal Details",
              required: true,
              repeatable: false,
              minimumRecords: 0,
              sortOrder: 10,
              status: "IN_PROGRESS",
              completedAt: null,
              completionSummary: "Upload identity evidence and complete personal details.",
              version: 0,
            },
            {
              id: randomUUID(),
              code: "QUALIFICATIONS",
              name: "Qualifications",
              required: true,
              repeatable: true,
              minimumRecords: 1,
              sortOrder: 20,
              status: "NOT_STARTED",
              completedAt: null,
              completionSummary: null,
              version: 0,
            },
            {
              id: randomUUID(),
              code: "DOCUMENTS",
              name: "Supporting Documents",
              required: false,
              repeatable: true,
              minimumRecords: 0,
              sortOrder: 30,
              status: "NOT_STARTED",
              completedAt: null,
              completionSummary: null,
              version: 0,
            },
          ],
          nextOfKin: [],
          employmentHistory: [],
          referees: [],
          qualifications: [],
          priorUzDeclaration: null,
          professionalAchievementsDeclaredNone: false,
          professionalAchievements: [],
          programmeEntryPreferences: [],
          documents: {
            requirements: evidenceScenario.requirements.map(([code, name]) => ({
              requirementCode: code,
              requirementName: name,
              required: true,
              captureSectionCode: "PERSONAL_DETAILS",
              applicantCategoryCodes: [evidenceScenario.categoryCode],
              state: uploadedRequirementCodes.has(code) ? "PENDING" : "MISSING",
              applicationDocumentId: null,
              documentId: requirementDocumentIds.get(code) ?? null,
              fileName: uploadedRequirementCodes.has(code) ? `${code.toLowerCase()}.pdf` : null,
              mimeType: uploadedRequirementCodes.has(code) ? "application/pdf" : null,
              checksumSha256: null,
              linkedAt: null,
              verifiedByUserId: null,
              verifiedAt: null,
              rejectionReason: null,
              documentVersion: 0,
              version: 0,
            })),
            requiredDocumentsUploaded:
              uploadedRequirementCodes.size === evidenceScenario.requirements.length,
            allRequiredDocumentsVerified: false,
          },
          readyForSubmission: false,
          missingRequirements: [],
          declarationAcceptedAt: null,
          declarationVersion: null,
          workflowProgress: { currentStageCode: "DRAFT", stages: [] },
        });

        await page.route(`**/api/admissions/applications/${applicationId}/workspace`, (route) =>
          route.fulfill({ json: workspace() }),
        );
        await page.route("**/api/admissions/applications/start-options**", (route) =>
          route.fulfill({
            json: { applicantCategories: [], applicationTypes: [], intakes: [], routes: [] },
          }),
        );
        await page.route("**/api/admissions/qualification-reference-data", (route) =>
          route.fulfill({
            json: { examBodies: [], oLevelSubjects: [], aLevelSubjects: [], otherSubjects: [] },
          }),
        );
        await page.route("**/api/core/reference/countries", (route) => route.fulfill({ json: [] }));
        await page.route("**/api/documents/uploads", async (route) => {
          const requestBody = route.request().postData() ?? "";
          const requirementCode = evidenceScenario.requirements.find(([code]) =>
            requestBody.includes(code),
          )?.[0];
          if (!requirementCode) throw new Error("Uploaded requirement code was not found.");
          const documentId = randomUUID();
          requirementDocumentIds.set(requirementCode, documentId);
          await route.fulfill({
            json: {
              id: documentId,
              extractionStatus: "COMPLETED",
              verificationStatus: "PENDING",
            },
          });
        });
        await page.route(
          `**/api/admissions/applications/${applicationId}/documents`,
          async (route) => {
            const body = route.request().postDataJSON() as { requirementCode: string };
            uploadedRequirementCodes.add(body.requirementCode);
            await route.fulfill({ json: {} });
          },
        );
        await page.route("**/api/documents/uploads/*/ocr-extraction", (route) =>
          route.fulfill({
            json: {
              documentId: route.request().url().split("/").at(-2),
              status: "COMPLETED",
              warningsJson: "[]",
            },
          }),
        );
        await page.route(
          `**/api/admissions/applications/${applicationId}/documents/*/prefill**`,
          (route) =>
            route.fulfill({
              json: {
                personalFields: {
                  dateOfBirth: "15/01/1999",
                  genderCode: "F",
                  ...(evidenceScenario.categoryCode === "INTERNATIONAL"
                    ? { passportNumber: "ab123456" }
                    : { nationalIdNumber: "12-345678-a-90" }),
                },
                qualificationResults: [],
                warnings: [],
                manualEntryAllowed: true,
              },
            }),
        );

        await page.goto(`${applicantPortalUrl}/applications/${applicationId}`);
        await expect(
          page.getByText("Upload the required identity evidence to continue", { exact: true }),
        ).toBeVisible();
        await expect(page.getByLabel("Date of birth")).not.toBeEditable();
        await expect(
          page
            .getByRole("navigation", { name: "Application process" })
            .getByRole("button", { name: /Supporting Documents/i }),
        ).toHaveCount(0);

        const evidencePath = testInfo.outputPath(
          `${evidenceScenario.categoryCode.toLowerCase()}-identity.pdf`,
        );
        mkdirSync(dirname(evidencePath), { recursive: true });
        writeFileSync(evidencePath, "%PDF-1.4\n% redacted identity fixture\n%%EOF\n");
        for (const [, requirementName] of evidenceScenario.requirements) {
          const evidenceCard = page
            .getByRole("heading", { name: new RegExp(`^${requirementName}`) })
            .locator("xpath=ancestor::section[1]");
          const fileChooserPromise = page.waitForEvent("filechooser");
          await evidenceCard
            .getByRole("button", { name: /Drop the document here or click to choose/ })
            .click();
          const fileChooser = await fileChooserPromise;
          await Promise.all([
            page.waitForResponse(
              (response) =>
                response.url().includes("/api/documents/uploads") &&
                response.request().method() === "POST",
            ),
            fileChooser.setFiles(evidencePath),
          ]);
          await expect(evidenceCard.getByText("Ready to review", { exact: true })).toBeVisible();
        }

        await expect(
          page.getByText("Upload the required identity evidence to continue", { exact: true }),
        ).toHaveCount(0);
        await expect(page.getByLabel("Date of birth")).toBeEditable();
        await expect(page.getByLabel("Date of birth")).toHaveValue("1999-01-15");
        await expect(page.getByLabel("First name")).not.toBeEditable();
        await expect(page.getByLabel("Last name")).not.toBeEditable();
        await expect(page.getByLabel("Applicant category")).not.toBeEditable();
        if (evidenceScenario.categoryCode === "INTERNATIONAL") {
          await expect(page.getByLabel("Passport number")).toHaveValue("AB123456");
        } else {
          await expect(page.getByLabel("National ID number")).toHaveValue("12-345678-A-90");
        }
      } finally {
        await cleanupApplicantLoginFixture(fixture);
      }
    });
  }

  test("compares UNDERGRAD, POSTGRAD, MBA, and EDUCATION before creating a draft", async ({
    page,
  }, testInfo) => {
    test.setTimeout(60_000);
    let fixture: ApplicantLoginFixture | null = null;
    try {
      fixture = await createApplicantLoginFixture();
      const intakeId = randomUUID();
      const intake = {
        id: intakeId,
        code: "AUG-2027",
        name: "August 2027 intake",
        startsOn: "2027-08-18",
        endsOn: "2027-07-31",
        maximumProgrammeChoices: 3,
      };
      const routeDefinitions = [
        {
          code: "UNDERGRAD",
          name: "Undergraduate",
          feeRequired: true,
          sections: [
            ["PERSONAL_DETAILS", "Applicant details", 0],
            ["NEXT_OF_KIN", "Next of kin", 1],
            ["QUALIFICATIONS", "Qualifications", 1],
            ["PROGRAMME_CHOICES", "Programme choices", 1],
            ["DOCUMENTS", "Supporting documents", 0],
            ["PAYMENT", "Application fee", 0],
            ["REVIEW_DECLARATION", "Review and declaration", 0],
          ],
        },
        {
          code: "POSTGRAD",
          name: "Postgraduate",
          feeRequired: false,
          sections: [
            ["PERSONAL_DETAILS", "Applicant details", 0],
            ["NEXT_OF_KIN", "Next of kin", 1],
            ["QUALIFICATIONS", "Qualifications", 1],
            ["EMPLOYMENT_HISTORY", "Employment history", 1],
            ["REFEREES", "Referees", 2],
            ["PROGRAMME_CHOICES", "Programme choices", 1],
            ["DOCUMENTS", "Supporting documents", 0],
            ["REVIEW_DECLARATION", "Review and declaration", 0],
          ],
        },
        {
          code: "MBA",
          name: "Master of Business Administration",
          feeRequired: true,
          sections: [
            ["PERSONAL_DETAILS", "Applicant details", 0],
            ["NEXT_OF_KIN", "Next of kin", 1],
            ["QUALIFICATIONS", "Qualifications", 1],
            ["PRIOR_UZ_STUDY", "Previous UZ study", 1],
            ["PROFESSIONAL_ACHIEVEMENTS", "Professional achievements", 1],
            ["EMPLOYMENT_HISTORY", "Employment history", 1],
            ["REFEREES", "Referees", 3],
            ["PROGRAMME_CHOICES", "Programme choices", 1],
            ["DOCUMENTS", "Supporting documents", 0],
            ["PAYMENT", "Application fee", 0],
            ["REVIEW_DECLARATION", "Review and declaration", 0],
          ],
        },
        {
          code: "EDUCATION",
          name: "Education",
          feeRequired: false,
          sections: [
            ["PERSONAL_DETAILS", "Applicant details", 0],
            ["NEXT_OF_KIN", "Next of kin", 1],
            ["QUALIFICATIONS", "Qualifications", 1],
            ["EMPLOYMENT_HISTORY", "Employment history", 1],
            ["REFEREES", "Referees", 3],
            ["PROGRAMME_CHOICES", "Programme choices", 1],
            ["DOCUMENTS", "Supporting documents", 0],
            ["REVIEW_DECLARATION", "Review and declaration", 0],
          ],
        },
      ].map((definition, routeIndex) => ({
        ...definition,
        id: randomUUID(),
        routeIndex,
        sections: definition.sections.map(([code, name, minimumRecords], sectionIndex) => ({
          code,
          name,
          required: true,
          repeatable: minimumRecords > 0,
          minimumRecords,
          sortOrder: (sectionIndex + 1) * 10,
        })),
      }));
      let routesAvailable = false;
      await page.route("**/api/admissions/applications/start-options**", (route) =>
        route.fulfill({
          json: {
            applicantCategoryCode: "LOCAL",
            applicantCategories: [{ code: "LOCAL", label: "Local applicant" }],
            intakes: [intake],
            applicationTypes: routeDefinitions.map((definition) => ({
              id: definition.id,
              code: definition.code,
              name: definition.name,
              requiresEmploymentHistory: definition.sections.some(
                (section) => section.code === "EMPLOYMENT_HISTORY",
              ),
              requiresReferees: definition.sections.some((section) => section.code === "REFEREES"),
              fee: definition.feeRequired
                ? { required: true, amount: 25, currencyCode: "USD" }
                : { required: false, amount: null, currencyCode: null },
              sections: definition.sections,
            })),
            routes: routesAvailable
              ? routeDefinitions.map((definition) => ({
                  applicationTypeId: definition.id,
                  applicationTypeCode: definition.code,
                  applicationTypeName: definition.name,
                  intakeId,
                  intakeCode: intake.code,
                  intakeName: intake.name,
                  maximumProgrammeChoices: 3,
                  programmes: Array.from(
                    { length: definition.routeIndex + 2 },
                    (_, programmeIndex) => ({
                      id: randomUUID(),
                      code: `${definition.code}-${programmeIndex + 1}`,
                      name: `${definition.name} Programme ${programmeIndex + 1}`,
                    }),
                  ),
                }))
              : [],
          },
        }),
      );

      await page.goto(applicantPortalUrl);
      await login(page, fixture);
      await page.getByRole("link", { name: "Start application" }).first().click();

      await expect(page.getByText("No application route is currently open")).toBeVisible();
      routesAvailable = true;
      await page
        .getByRole("button", { name: "Check again" })
        .evaluate((element: HTMLElement) => element.click());
      await expect(
        page.getByRole("heading", {
          name: "Choose your application route",
          exact: true,
        }),
      ).toBeVisible();
      for (const routeDefinition of routeDefinitions) {
        const routeCard = page.getByTestId(`application-route-${routeDefinition.code}`);
        await expect(routeCard).toContainText(routeDefinition.name);
        await routeCard.evaluate((element: HTMLElement) =>
          element.scrollIntoView({ block: "center" }),
        );
        await expect(routeCard).toBeInViewport();
        await routeCard.evaluate((element: HTMLElement) => element.click());
        await expect(routeCard).toHaveAttribute("aria-pressed", "true");
        const evidenceSummary = page.getByTestId("selected-route-evidence");
        if (routeDefinition.code === "UNDERGRAD") {
          await expect(evidenceSummary).toContainText("No confidential references");
        }
        if (routeDefinition.code === "POSTGRAD") {
          await expect(evidenceSummary).toContainText("2 confidential references");
        }
        if (routeDefinition.code === "MBA") {
          await expect(evidenceSummary).toContainText("Previous UZ study");
          await expect(evidenceSummary).toContainText("Professional achievements");
          await expect(evidenceSummary).toContainText("3 confidential references");
        }
        if (routeDefinition.code === "EDUCATION") {
          await expect(evidenceSummary).toContainText("Employment history");
          await expect(evidenceSummary).toContainText("3 confidential references");
        }
      }
      await expect(page.getByLabel("Intake")).toBeEnabled();
      await page.screenshot({
        path: testInfo.outputPath("application-route-comparison.png"),
        fullPage: true,
      });
    } finally {
      await cleanupApplicantLoginFixture(fixture);
    }
  });

  for (const routeExpectation of [
    { route: "POSTGRAD" as const, referenceCount: 2 },
    { route: "EDUCATION" as const, referenceCount: 3 },
  ]) {
    test(`creates a ${routeExpectation.route} draft with its governed evidence threshold`, async ({
      page,
    }) => {
      test.setTimeout(90_000);
      let fixture: ApplicantFixture | null = null;
      try {
        fixture = await createFixture({ route: routeExpectation.route });
        const failedApiResponses: string[] = [];
        page.on("response", (response) => {
          if (response.url().includes("/api/") && response.status() >= 500) {
            failedApiResponses.push(
              `${response.status()} ${response.request().method()} ${response.url()}`,
            );
          }
        });

        await page.goto(applicantPortalUrl);
        await login(page, fixture);
        await page.getByRole("link", { name: "Start application" }).first().click();
        await selectApplicationRoute(page, fixture.applicationTypeName);
        await selectOption(page, "Intake", fixture.intakeName);
        await Promise.all([
          page.waitForURL(`${applicantPortalUrl}/applications/**`),
          page
            .getByRole("button", { name: "Create draft", exact: true })
            .evaluate((element: HTMLElement) => element.click()),
        ]);

        const workspaceNavigator = page.getByRole("navigation", {
          name: "Application process",
        });
        await expect(
          workspaceNavigator.getByRole("button", {
            name: /Employment history/,
          }),
        ).toBeVisible();
        await expect(workspaceNavigator.getByRole("button", { name: /Referees/ })).toBeVisible();
        await expect(
          workspaceNavigator.getByRole("button", { name: /Previous UZ study/ }),
        ).toHaveCount(0);
        await expect(
          workspaceNavigator.getByRole("button", {
            name: /Professional achievements/,
          }),
        ).toHaveCount(0);
        const refereeStep = workspaceNavigator.getByRole("button", {
          name: /Referees/,
        });
        await expect(refereeStep).toBeEnabled();
        await refereeStep.evaluate((element: HTMLElement) => element.click());
        await expect(page.getByRole("heading", { name: "Referees", exact: true })).toBeVisible();
        await expect(
          page
            .locator("#application-section-editor")
            .getByText(`0 of ${routeExpectation.referenceCount} required record(s) captured.`, {
              exact: true,
            }),
        ).toBeVisible();
        await expect(page.getByTestId("application-context")).toContainText(
          fixture.applicationTypeName,
        );
        expect(failedApiResponses).toEqual([]);
      } finally {
        await cleanupFixture(fixture);
      }
    });
  }

  test("loads the authenticated applicant dashboard without service contract errors", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    let fixture: ApplicantFixture | null = null;
    try {
      fixture = await createFixture();
      const failedApiResponses: string[] = [];
      page.on("response", (response) => {
        if (response.url().includes("/api/") && response.status() >= 500) {
          failedApiResponses.push(
            `${response.status()} ${response.request().method()} ${response.url()}`,
          );
        }
      });

      await page.goto(applicantPortalUrl);
      await login(page, fixture);

      await expect(
        page.getByRole("heading", { name: "Browser Applicant", exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("link", { name: "Start application", exact: true }).first(),
      ).toBeVisible();
      await expect(
        page.getByRole("heading", {
          name: "Applications unavailable",
          exact: true,
        }),
      ).toHaveCount(0);
      expect(failedApiResponses).toEqual([]);
    } finally {
      await cleanupFixture(fixture);
    }
  });

  test("shows offer-letter actions only when a current portal publication is available", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    let fixture: ApplicantFixture | null = null;
    try {
      fixture = await createFixture();
      await page.goto(applicantPortalUrl);
      await login(page, fixture);

      const publishedOfferId = randomUUID();
      const unpublishedOfferId = randomUUID();
      const publishedApplicationId = randomUUID();
      const publicationId = randomUUID();
      const documentVersionId = randomUUID();
      const generatedDocumentId = randomUUID();
      const commonOffer = {
        offerBatchId: null,
        applicationId: publishedApplicationId,
        applicationNumber: "EMH-OFFER-UI-0001",
        applicantNumber: "APP-OFFER-UI-0001",
        applicantName: "Browser Applicant",
        programmeChoiceId: randomUUID(),
        programmeId: fixture.programmeId,
        programmeVersionId: fixture.programmeVersionId,
        programmeCode: fixture.programmeCode,
        programmeName: "Browser Verified Programme",
        intakeId: fixture.intakeId,
        offerType: "FIRM",
        amendmentPending: false,
        conditionsText: null,
        acceptanceDeadline: "2099-12-31T23:59:59Z",
        registrationDate: null,
        orientationDate: null,
        commencementDate: "2099-01-15",
        approvedAt: "2098-11-01T10:00:00Z",
        sentAt: "2098-11-02T10:00:00Z",
        expiredAt: null,
        expiryReason: null,
        conversionRequestedAt: null,
        conversionRequestId: null,
        convertedStudentId: null,
        convertedStudentNumber: null,
        convertedAt: null,
        conditions: [],
      };

      await page.route("**/api/admissions/offers/mine", (route) =>
        route.fulfill({
          json: [
            {
              ...commonOffer,
              id: publishedOfferId,
              offerNumber: "OFR-PUBLISHED-0001",
              status: "ACCEPTED",
              currentDocumentVersionId: documentVersionId,
              currentPublicationId: publicationId,
              generatedDocumentId,
              response: {
                response: "ACCEPTED",
                respondedAt: "2098-11-03T10:00:00Z",
                notes: null,
              },
            },
            {
              ...commonOffer,
              id: unpublishedOfferId,
              offerNumber: "OFR-PREPARING-0002",
              status: "SENT",
              currentDocumentVersionId: null,
              currentPublicationId: null,
              generatedDocumentId: null,
              response: null,
            },
          ],
        }),
      );
      await page.route(
        `**/api/admissions/applicant/offers/${publishedOfferId}/published-document`,
        (route) =>
          route.fulfill({
            json: {
              offerId: publishedOfferId,
              publicationId,
              generatedDocumentId,
              documentNumber: "OFR-PUBLISHED-0001-V1",
              checksumSha256: "e2e-checksum",
            },
          }),
      );
      await page.route(
        `**/api/documents/${generatedDocumentId}/applicant-download?disposition=inline`,
        (route) =>
          route.fulfill({
            json: { downloadUrl: `${applicantPortalUrl}/e2e-offer-letter.pdf` },
          }),
      );

      await page.reload();
      await expect(
        page.getByRole("heading", { name: "Admission offers", exact: true }),
      ).toBeVisible();

      const publishedOffer = page.getByTestId(`admission-offer-${publishedOfferId}`);
      await expect(
        publishedOffer.getByRole("button", { name: "Preview", exact: true }),
      ).toBeVisible();
      await expect(
        publishedOffer.getByRole("button", { name: "Download", exact: true }),
      ).toBeVisible();
      await expect(
        publishedOffer.getByRole("button", {
          name: "Accept offer",
          exact: true,
        }),
      ).toHaveCount(0);
      await expect(
        publishedOffer.getByRole("button", { name: "Decline", exact: true }),
      ).toHaveCount(0);

      const publishedDocumentRequest = page.waitForRequest((request) =>
        request
          .url()
          .includes(`/api/admissions/applicant/offers/${publishedOfferId}/published-document`),
      );
      const offerPreviewPagePromise = page.context().waitForEvent("page");
      await publishedOffer.getByRole("button", { name: "Preview", exact: true }).click();
      await publishedDocumentRequest;
      const offerPreviewPage = await offerPreviewPagePromise;
      await expect(offerPreviewPage).toHaveURL(`${applicantPortalUrl}/e2e-offer-letter.pdf`);
      await offerPreviewPage.close();

      const unpublishedOffer = page.getByTestId(`admission-offer-${unpublishedOfferId}`);
      await expect(
        unpublishedOffer.getByText("Offer letter being prepared", {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        unpublishedOffer.getByRole("button", { name: "Preview", exact: true }),
      ).toHaveCount(0);
      await expect(
        unpublishedOffer.getByRole("button", { name: "Download", exact: true }),
      ).toHaveCount(0);
      await expect(
        unpublishedOffer.getByRole("button", {
          name: "Accept offer",
          exact: true,
        }),
      ).toHaveCount(0);
      await expect(
        unpublishedOffer.getByRole("button", { name: "Decline", exact: true }),
      ).toHaveCount(0);

      await page.route(
        `**/api/admissions/applications/${publishedApplicationId}/workspace`,
        (route) =>
          route.fulfill({
            json: {
              application: {
                id: publishedApplicationId,
                applicationNumber: commonOffer.applicationNumber,
                applicantNumber: commonOffer.applicantNumber,
                applicantName: commonOffer.applicantName,
                intakeId: fixture!.intakeId,
                intakeCode: `BI_${fixture!.codeSuffix}`,
                applicationTypeId: fixture!.applicationTypeId,
                applicationTypeName: fixture!.applicationTypeName,
                status: "OFFERED",
                paymentRequired: false,
                paymentClearanceStatus: "NOT_REQUIRED",
                paymentWaiverReason: null,
                canSubmit: false,
                canEnterReview: true,
                calculatedTotalPoints: null,
                pointsCalculatedAt: null,
                programmeChoices: [],
                payment: null,
              },
              profile: {
                id: randomUUID(),
                userId: randomUUID(),
                applicantNumber: commonOffer.applicantNumber,
                applicantCategoryCode: "LOCAL",
                titleCode: "MR",
                firstName: "Browser",
                middleNames: null,
                lastName: "Applicant",
                dateOfBirth: "1999-01-15",
                genderCode: "MALE",
                maritalStatusCode: "SINGLE",
                nationalIdNumber: "99-OFFER-UI",
                passportNumber: null,
                countryId: fixture!.countryId,
                nationalityCountryId: fixture!.countryId,
                placeOfBirth: "Harare",
                disabilityStatusCode: "NONE",
                specialNeeds: null,
                sponsorTypeCode: "SELF",
                primaryEmail: fixture!.username,
                primaryPhone: "+263772000001",
                postalAddress: null,
                residentialAddress: "Harare",
                completenessPercentage: 100,
                missingRequiredFields: [],
                createdAt: "2098-10-01T10:00:00Z",
                updatedAt: "2098-11-03T10:00:00Z",
                version: 0,
              },
              sections: [
                {
                  id: randomUUID(),
                  code: "REVIEW_DECLARATION",
                  name: "Review and declaration",
                  required: true,
                  repeatable: false,
                  minimumRecords: 0,
                  sortOrder: 90,
                  status: "COMPLETE",
                  completedAt: "2098-10-02T10:00:00Z",
                  completionSummary: "Application submitted.",
                  version: 0,
                },
              ],
              nextOfKin: [],
              employmentHistory: [],
              referees: [],
              priorUzDeclaration: null,
              professionalAchievementsDeclaredNone: true,
              professionalAchievements: [],
              programmeEntryPreferences: [],
              qualifications: [],
              documents: {
                requirements: [],
                requiredDocumentsUploaded: true,
                allRequiredDocumentsVerified: true,
              },
              readyForSubmission: false,
              missingRequirements: [],
              declarationAcceptedAt: "2098-10-02T10:00:00Z",
              declarationVersion: "2026.1",
              workflowProgress: {
                currentStageCode: "OFFER",
                stages: [],
              },
            },
          }),
      );
      await page.route("**/api/admissions/applications/start-options**", (route) =>
        route.fulfill({
          json: { applicantCategories: [], applicationTypes: [], intakes: [], routes: [] },
        }),
      );
      await page.route("**/api/admissions/qualification-reference-data", (route) =>
        route.fulfill({
          json: { examBodies: [], oLevelSubjects: [], aLevelSubjects: [], otherSubjects: [] },
        }),
      );
      await page.route("**/api/core/reference/countries", (route) => route.fulfill({ json: [] }));

      await page.goto(`${applicantPortalUrl}/applications/${publishedApplicationId}`);
      const previewFromWorkspace = page.getByRole("button", {
        name: "Preview offer letter",
        exact: true,
      });
      await expect(previewFromWorkspace).toBeVisible();
      await expect(
        page.getByRole("button", { name: "Download offer letter", exact: true }),
      ).toBeVisible();

      const workspacePreviewPagePromise = page.context().waitForEvent("page");
      await previewFromWorkspace.click();
      const workspacePreviewPage = await workspacePreviewPagePromise;
      await expect(workspacePreviewPage).toHaveURL(`${applicantPortalUrl}/e2e-offer-letter.pdf`);
      await workspacePreviewPage.close();
    } finally {
      await cleanupFixture(fixture);
    }
  });

  test("shows manual payment evidence and keeps card checkout inside the application", async ({
    page,
  }) => {
    test.setTimeout(90_000);
    let fixture: ApplicantFixture | null = null;
    try {
      fixture = await createFixture();
      await page.goto(applicantPortalUrl);
      await login(page, fixture);

      const applicationId = randomUUID();
      const financePaymentReferenceId = randomUUID();
      const checkoutAttemptId = randomUUID();
      let paymentConfirmed = false;
      let reconciledAttemptId: string | undefined;
      await page.route(`**/api/admissions/applications/${applicationId}/workspace`, (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            application: {
              id: applicationId,
              applicationNumber: "EMH-PAYMENT-UI-0001",
              applicantNumber: "APP-PAYMENT-UI-0001",
              applicantName: "Browser Applicant",
              intakeId: fixture!.intakeId,
              intakeCode: `BI_${fixture!.codeSuffix}`,
              applicationTypeId: fixture!.applicationTypeId,
              applicationTypeName: fixture!.applicationTypeName,
              status: "DRAFT",
              paymentRequired: true,
              paymentClearanceStatus: paymentConfirmed ? "PAID" : "PENDING",
              paymentWaiverReason: null,
              canSubmit: false,
              canEnterReview: false,
              calculatedTotalPoints: null,
              pointsCalculatedAt: null,
              programmeChoices: [],
              payment: {
                financePaymentReferenceId,
                reference: "EMH-PAY-0000000442",
                amountDue: 25,
                currencyCode: "USD",
                baseCurrencyCode: "USD",
                baseAmountDue: 25,
                ratingStatus: "RATED",
                status: paymentConfirmed ? "PAID" : "PENDING",
                requiredForSubmission: true,
                workflowCleared: paymentConfirmed,
                paidAt: paymentConfirmed ? "2026-08-10T08:43:33Z" : null,
              },
            },
            profile: {
              id: randomUUID(),
              userId: randomUUID(),
              applicantNumber: "APP-PAYMENT-UI-0001",
              applicantCategoryCode: "LOCAL",
              titleCode: "MR",
              firstName: "Browser",
              middleNames: null,
              lastName: "Applicant",
              dateOfBirth: "1999-01-15",
              genderCode: "MALE",
              maritalStatusCode: "SINGLE",
              nationalIdNumber: "99-UI",
              passportNumber: null,
              countryId: fixture!.countryId,
              nationalityCountryId: fixture!.countryId,
              placeOfBirth: "Harare",
              disabilityStatusCode: "NONE",
              specialNeeds: null,
              sponsorTypeCode: "SELF",
              primaryEmail: fixture!.username,
              primaryPhone: "+263772000001",
              postalAddress: null,
              residentialAddress: "Harare",
              completenessPercentage: 100,
              missingRequiredFields: [],
              createdAt: "2026-08-10T06:00:00Z",
              updatedAt: "2026-08-10T06:00:00Z",
              version: 0,
            },
            sections: [
              {
                id: randomUUID(),
                code: "PAYMENT",
                name: "Application fee",
                required: true,
                repeatable: false,
                minimumRecords: 0,
                sortOrder: 80,
                status: paymentConfirmed ? "COMPLETE" : "IN_PROGRESS",
                completedAt: paymentConfirmed ? "2026-08-10T08:43:33Z" : null,
                completionSummary: paymentConfirmed
                  ? "Application fee confirmed."
                  : "Application fee confirmation or waiver is required.",
                version: 0,
              },
            ],
            nextOfKin: [],
            employmentHistory: [],
            referees: [],
            qualifications: [],
            priorUzDeclaration: null,
            professionalAchievementsDeclaredNone: false,
            professionalAchievements: [],
            documents: {
              requirements: [],
              requiredDocumentsUploaded: true,
              allRequiredDocumentsVerified: false,
            },
            readyForSubmission: false,
            missingRequirements: [
              "Application fee: Application fee confirmation or waiver is required.",
            ],
            declarationAcceptedAt: null,
            declarationVersion: null,
          }),
        }),
      );
      await page.route("**/api/admissions/applications/start-options**", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            applicantCategories: [],
            applicationTypes: [],
            intakes: [],
            routes: [],
          }),
        }),
      );
      await page.route("**/api/admissions/qualification-reference-data", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            examBodies: [],
            oLevelSubjects: [],
            aLevelSubjects: [],
            otherSubjects: [],
          }),
        }),
      );
      await page.route("**/api/core/reference/countries", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "[]",
        }),
      );
      await page.route(
        `**/api/finance/application-payment-references/by-application/${applicationId}/payment-options`,
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              proofOfPaymentUploadAvailable: true,
              onlinePayment: {
                available: true,
                availabilityMessage: "Pay the application fee securely by debit or credit card.",
              },
            }),
          }),
      );
      await page.route(
        `**/api/finance/application-payment-references/by-application/${applicationId}/online-checkouts`,
        (route) =>
          route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify({
              attemptId: checkoutAttemptId,
              embeddedCheckoutUrl: "https://portal.host.iveri.com/Lite/LiteBox",
              returnMessageOrigin: new URL(applicantPortalUrl).origin,
              formParameters: {
                Lite_Merchant_ApplicationId: "{00000000-0000-0000-0000-000000000001}",
                Lite_Order_Amount: "2500",
                Lite_ConsumerOrderID_PreFix: "EMH",
                Lite_Merchant_Trace: "payment-ui-trace",
                Ecom_BillTo_Online_Email: fixture!.username,
              },
              expiresAt: "2026-08-10T07:30:00Z",
            }),
          }),
      );
      await page.route(
        `**/api/finance/application-payment-references/by-application/${applicationId}/online-checkouts/reconcile`,
        (route) => {
          const request = route.request().postDataJSON() as {
            attemptId?: string;
          };
          if (request.attemptId) {
            reconciledAttemptId = request.attemptId;
            paymentConfirmed = true;
          }
          return route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              status: paymentConfirmed ? "PAID" : "PENDING",
              workflowCleared: paymentConfirmed,
            }),
          });
        },
      );
      await page.route("https://portal.host.iveri.com/Lite/LiteBox", (route) =>
        route.fulfill({
          status: 200,
          contentType: "text/html",
          body: `<!doctype html><html><body><main></main><script>
          addEventListener('message', event => {
            const request = JSON.parse(event.data)
            const fields = Object.fromEntries(request.form.map(field => [field.name, field.value]))
            if (!fields.Lite_ConsumerOrderID_PreFix) return
            document.querySelector('main').innerHTML = '<label>Card Number <input name="cardNumber"></label>'
          })
        </script></body></html>`,
        }),
      );
      await page.route(
        `**/api/documents/uploads?ownerType=FINANCE_RECORD&ownerId=${financePaymentReferenceId}`,
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: "[]",
          }),
      );

      await page.goto(`${applicantPortalUrl}/applications/${applicationId}`);
      await expect(
        page.getByRole("heading", { name: "Application fee", exact: true }),
      ).toBeVisible();
      await expect(page.getByText("EMH-PAY-0000000442", { exact: true })).toBeVisible();
      await expect(
        page.getByRole("heading", {
          name: "Already paid by bank?",
          exact: true,
        }),
      ).toBeVisible();
      await expect(page.getByLabel("Proof of payment")).toBeVisible();
      await expect(page.getByRole("heading", { name: "Pay online", exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "Pay USD 25 now", exact: true })).toBeVisible();
      await expect(page.getByText(/iVeri/i)).toHaveCount(0);

      await page.getByRole("button", { name: "Pay USD 25 now", exact: true }).click();
      await expect(page.getByRole("dialog", { name: "Make payment" })).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "Pay USD 25", exact: true })).toBeVisible();
      await expect(page.getByTitle("Secure card payment")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancel payment", exact: true })).toBeVisible();
      await expect(page).toHaveURL(`${applicantPortalUrl}/applications/${applicationId}`);

      const checkoutFrame = page
        .frames()
        .find((frame) => frame.url() === "https://portal.host.iveri.com/Lite/LiteBox");
      expect(checkoutFrame).toBeTruthy();
      await expect(checkoutFrame!.getByLabel("Card Number")).toBeVisible();
      await checkoutFrame!.evaluate((merchantSiteOrigin) => {
        window.parent.postMessage(
          JSON.stringify({
            Lite_Payment_Card_Status: "0",
            Lite_Merchant_Trace: "payment-ui-trace",
          }),
          merchantSiteOrigin,
        );
      }, new URL(applicantPortalUrl).origin);
      await expect(page.getByTitle("Secure card payment")).toBeHidden();
      await expect(page.getByRole("heading", { name: "Payment confirmed" })).toBeVisible();
      expect(reconciledAttemptId).toBe(checkoutAttemptId);
      await expect(page.getByText("Application fee confirmed", { exact: true })).toBeVisible();
    } finally {
      await cleanupFixture(fixture);
    }
  });

  test("selects an Academic Setup programme and persists its curriculum snapshot", async ({
    page,
  }, testInfo) => {
    test.setTimeout(60_000);
    let fixture: ApplicantFixture | null = null;
    try {
      fixture = await createFixture();
      const consoleErrors: string[] = [];
      page.on(
        "console",
        (message) => message.type() === "error" && consoleErrors.push(message.text()),
      );
      await page.goto(applicantPortalUrl);
      await login(page, fixture);
      await page.getByRole("link", { name: "Start application" }).first().click();
      await expect(page).toHaveURL(`${applicantPortalUrl}/applications/new`);
      await expect(page.getByRole("dialog")).toHaveCount(0);

      const form = page.locator("#application-start-journey");
      await selectApplicationRoute(page, fixture.applicationTypeName);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        ),
      ).toBeLessThanOrEqual(1);
      const startJourneyNavigator = page.getByRole("navigation", {
        name: "Application process",
      });
      await expect(
        startJourneyNavigator.getByRole("button", {
          name: /Application route/,
        }),
      ).toBeVisible();
      await expect(
        startJourneyNavigator.getByRole("button", {
          name: /Applicant details/,
        }),
      ).toBeVisible();
      await expect(
        startJourneyNavigator.getByRole("button", {
          name: /Programme choices/,
        }),
      ).toBeVisible();
      await expect(
        startJourneyNavigator.getByRole("button", { name: /Personal details/ }),
      ).toHaveCount(0);
      await expect(
        startJourneyNavigator.getByRole("button", { name: /Qualifications/ }),
      ).toBeVisible();
      await expect(
        startJourneyNavigator.getByRole("button", {
          name: /Review and declaration/,
        }),
      ).toBeVisible();
      await form.getByLabel("Intake").click();
      await page.getByRole("option", { name: fixture.intakeName, exact: true }).click();

      await Promise.all([
        page.waitForURL(`${applicantPortalUrl}/applications/**`),
        page
          .getByRole("button", { name: "Create draft", exact: true })
          .evaluate((element: HTMLElement) => element.click()),
      ]);

      const workspaceNavigator = page.getByRole("navigation", {
        name: "Application process",
      });
      await expect(
        workspaceNavigator.getByRole("button", { name: /Application setup/ }),
      ).toHaveCount(0);
      await expect(workspaceNavigator.getByRole("button").first()).toContainText(
        "Application route",
      );
      await expect(
        workspaceNavigator.getByRole("button", { name: /Applicant details/ }),
      ).toBeVisible();
      await expect(
        workspaceNavigator.getByRole("button", { name: /Personal details/ }),
      ).toHaveCount(0);
      await expect(
        page.getByRole("heading", { name: "Applicant details", exact: true }),
      ).toBeVisible();
      const profileSaveRequests: string[] = [];
      page.on("request", (request) => {
        if (request.method() === "PUT" && request.url().includes("/profile"))
          profileSaveRequests.push(request.url());
      });
      await expect(page.getByLabel("First name")).not.toBeEditable();
      await expect(page.getByLabel("First name")).toHaveValue("Browser");
      await expect(page.getByLabel("Last name")).not.toBeEditable();
      await expect(page.getByLabel("Last name")).toHaveValue("Applicant");
      await page.waitForTimeout(1_100);
      expect(profileSaveRequests).toEqual([]);
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await workspaceNavigator.getByRole("button", { name: /Next of kin/ }).click();
      await expect(
        page.getByRole("heading", { name: "Next of kin details", exact: true }),
      ).toBeVisible();
      await expect(page.getByLabel("Full name")).toBeVisible();
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await workspaceNavigator
        .getByRole("button", { name: /Programme choices/ })
        .evaluate((element: HTMLElement) => element.click());
      await expect(
        page.getByRole("heading", {
          name: "Programme choices",
          exact: true,
          level: 1,
        }),
      ).toBeVisible();
      await page.getByLabel("Programme choices").click();
      await expect(
        page.getByRole("option", { name: new RegExp(fixture.programmeCode) }),
      ).toBeVisible();
      await page.getByRole("option", { name: new RegExp(fixture.programmeCode) }).click();
      await page.keyboard.press("Escape");
      await expect(page.getByRole("listbox")).toBeHidden();
      await page.getByRole("button", { name: "Save choices", exact: true }).click();
      await dismissSuccessDialog(page);
      await expect(
        page
          .getByText(`${fixture.programmeCode} · Browser Verified Programme`, {
            exact: true,
          })
          .last(),
      ).toBeVisible();
      await expect(
        page
          .locator("#application-section-editor")
          .getByText(`Department of Computing · Curriculum ${fixture.calendarYear}.1`, {
            exact: true,
          }),
      ).toBeVisible();
      await page.screenshot({
        path: testInfo.outputPath("applicant-programme-choice.png"),
        fullPage: true,
      });
      expect(consoleErrors).toEqual([]);
    } finally {
      await cleanupFixture(fixture);
    }
  });

  test("completes the draft workspace as one sequential applicant journey", async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    let fixture: ApplicantFixture | null = null;
    try {
      fixture = await createFixture();
      const consoleErrors: string[] = [];
      const failedResponses: string[] = [];
      page.on(
        "console",
        (message) => message.type() === "error" && consoleErrors.push(message.text()),
      );
      page.on("response", (response) => {
        if (response.url().includes("/api/") && response.status() >= 400) {
          failedResponses.push(
            `${response.status()} ${response.request().method()} ${response.url()}`,
          );
        }
      });

      await page.goto(applicantPortalUrl);
      await login(page, fixture);
      await page.getByRole("link", { name: "Start application" }).first().click();

      const form = page.locator("#application-start-journey");
      await expect(page.getByText(fixture.applicationTypeName, { exact: true })).toBeVisible();
      await selectApplicationRoute(page, fixture.applicationTypeName);
      await selectOption(page, "Intake", fixture.intakeName);
      await Promise.all([
        page.waitForURL(`${applicantPortalUrl}/applications/**`),
        page
          .getByRole("button", { name: "Create draft", exact: true })
          .evaluate((element: HTMLElement) => element.click()),
      ]);

      const workspaceNavigator = page.getByRole("navigation", {
        name: "Application process",
      });
      await expect(
        workspaceNavigator.getByRole("button", { name: /Application setup/ }),
      ).toHaveCount(0);
      await expect(workspaceNavigator.getByRole("button").first()).toContainText(
        "Application route",
      );
      await expect(
        workspaceNavigator.getByRole("button", { name: /Applicant details/ }),
      ).toBeVisible();
      await expect(
        workspaceNavigator.getByRole("button", { name: /Personal details/ }),
      ).toHaveCount(0);
      await expect(
        page.getByRole("heading", { name: "Applicant details", exact: true }),
      ).toBeVisible();
      await expect(page.getByLabel("First name")).not.toBeEditable();
      await expect(page.getByLabel("First name")).toHaveValue("Browser");
      await expect(page.getByLabel("Last name")).not.toBeEditable();
      await expect(page.getByLabel("Last name")).toHaveValue("Applicant");
      await uploadRequiredPersonalEvidence(page, testInfo, fixture.codeSuffix);

      const profileSave = page.waitForResponse(
        (response) =>
          response.url().includes("/api/admissions/applications/") &&
          response.url().endsWith("/profile") &&
          response.request().method() === "PUT" &&
          response.ok(),
      );
      await selectOption(page, "Title", "Mr");
      await page.getByLabel("Date of birth").fill("1999-01-15");
      await selectOptionUntilFieldContains(page, "Gender", "MALE", "MALE");
      await selectOptionUntilFieldContains(page, "Marital status", "SINGLE", "SINGLE");
      await page.getByLabel("National ID number").fill(`99-${fixture.codeSuffix}`);
      await selectOptionUntilFieldContains(
        page,
        "Country of residence",
        /ZW .*Zimbabwe/,
        "Zimbabwe",
      );
      await selectOptionUntilFieldContains(page, "Nationality", /ZW .*Zimbabwe/, "Zimbabwe");
      const activeSection = page.locator("#application-section-editor");
      await activeSection.getByLabel("Phone number").fill("+263772000001");
      await activeSection.getByLabel("Residential address").fill("630 Churchill Avenue, Harare");
      await profileSave;
      await expect(activeSection.getByText("Applicant details complete.")).toBeVisible();

      await clickVisibleButtonContaining(page, "Continue: Next of kin");
      await expect(page.getByRole("heading", { name: "Next of kin", exact: true })).toBeVisible();
      await expect(
        page.getByRole("heading", { name: "Next of kin details", exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await page.getByLabel("Full name").fill("Tariro Applicant");
      await selectOption(page, "Relationship", "PARENT");
      await page.getByLabel("Phone number").fill("+263772000002");
      await page.getByLabel("Email").fill(`kin-${fixture.codeSuffix.toLowerCase()}@example.test`);
      await page.getByLabel("Address").fill("Harare");
      await clickVisibleButtonContaining(page, "Save record");
      await expect(page.getByText("Tariro Applicant")).toBeVisible();

      await clickVisibleButtonContaining(page, "Continue: Qualifications");
      await expect(
        page.getByRole("heading", { name: "Qualifications", exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("heading", {
          name: "Add qualification",
          exact: true,
        }),
      ).toBeVisible();
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await completeSchoolQualification(
        page,
        testInfo,
        fixture,
        "Browser Test High School",
        "C1234",
        `N${fixture.codeSuffix}`,
      );

      await clickVisibleButtonContaining(page, "Continue: Programme choices");
      await expect(
        page.getByRole("heading", {
          name: "Programme choices",
          exact: true,
          level: 1,
        }),
      ).toBeVisible();
      await page
        .getByLabel("Programme choices")
        .evaluate((element: HTMLElement) => element.click());
      await expect(
        page.getByRole("option", { name: new RegExp(fixture.programmeCode) }),
      ).toBeVisible();
      await page
        .getByRole("option", { name: new RegExp(fixture.programmeCode) })
        .click({ force: true });
      await page.keyboard.press("Escape");
      await clickVisibleButtonContaining(page, "Save choices");
      await dismissSuccessDialog(page);
      await expect(
        page
          .getByText(`${fixture.programmeCode} · Browser Verified Programme`, {
            exact: true,
          })
          .last(),
      ).toBeVisible();
      await clickVisibleButtonContaining(page, "Continue: Review and declaration");

      await page.waitForFunction(() =>
        Array.from(document.querySelectorAll("h1")).some(
          (heading) => (heading.textContent ?? "").trim() === "Review and declaration",
        ),
      );
      await expect(
        activeSection.getByText("Review and accept the applicant declaration.", { exact: true }),
      ).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Application overview",
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByText(fixture.applicationTypeName, { exact: true }),
      ).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Applicant details",
          exact: true,
        }),
      ).toBeVisible();
      await expect(activeSection.getByText("Browser", { exact: true })).toBeVisible();
      await expect(activeSection.getByText("Applicant", { exact: true })).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Next of kin",
          exact: true,
        }),
      ).toBeVisible();
      await expect(activeSection.getByText("Tariro Applicant", { exact: true })).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Qualifications and results",
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByText(`English Language ${fixture.codeSuffix}`, {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByText(`Mathematics ${fixture.codeSuffix}`, {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Programme choices",
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByText(`${fixture.programmeCode} · Browser Verified Programme`, {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Supporting documents",
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        activeSection.getByText(`identity-evidence-${fixture.codeSuffix}.pdf`).first(),
      ).toBeVisible();
      await expect(
        activeSection.getByRole("heading", {
          name: "Application fee",
          exact: true,
        }),
      ).toBeVisible();

      const documentDownloadResponse = page.waitForResponse(
        (response) =>
          response.url().includes("/api/documents/uploads/") &&
          response.url().endsWith("/download") &&
          response.request().method() === "GET" &&
          response.ok(),
      );
      await activeSection
        .getByRole("button", { name: "Preview National ID", exact: true })
        .evaluate((element: HTMLElement) => element.click());
      await documentDownloadResponse;
      await expect(activeSection.getByTitle("National ID preview")).toBeVisible();
      await activeSection
        .getByRole("button", { name: "Close document preview", exact: true })
        .evaluate((element: HTMLElement) => element.click());
      await expect(activeSection.getByTitle("National ID preview")).toHaveCount(0);

      await clickVisibleButtonContaining(page, "Accept declaration");
      await clickVisibleButtonContaining(page, "Accept declaration");
      await expect(page.getByText("Ready for submission")).toBeVisible();

      await clickVisibleButtonContaining(page, "Submit application");
      await clickVisibleButtonContaining(page, "Submit application");
      await expect(page.getByRole("heading", { name: "Application submitted" })).toBeVisible();
      await page
        .getByRole("button", { name: "OK" })
        .evaluate((element: HTMLElement) => element.click());
      await expect(page).toHaveURL(applicantPortalUrl + "/");

      await page.screenshot({
        path: testInfo.outputPath("applicant-full-journey.png"),
        fullPage: true,
      });
      expect(failedResponses).toEqual([]);
      expect(consoleErrors).toEqual([]);
    } finally {
      await cleanupFixture(fixture);
    }
  });

  for (const routeScenario of [
    {
      route: "POSTGRAD" as const,
      programmeName: "Master of Data Science",
      referenceCount: 2,
      requiresMbaDeclarations: false,
    },
    {
      route: "MBA" as const,
      programmeName: "Master of Business Administration",
      referenceCount: 3,
      requiresMbaDeclarations: true,
    },
    {
      route: "EDUCATION" as const,
      programmeName: "Master of Education",
      referenceCount: 3,
      requiresMbaDeclarations: false,
    },
  ]) {
    test(`completes a ${routeScenario.route} application with governed evidence and confidential references`, async ({
      page,
      browser,
    }, testInfo) => {
      test.setTimeout(240_000);
      let fixture: ApplicantFixture | null = null;
      try {
        fixture = await createFixture({ route: routeScenario.route });
        const consoleErrors: string[] = [];
        const failedResponses: string[] = [];
        page.on(
          "console",
          (message) => message.type() === "error" && consoleErrors.push(message.text()),
        );
        page.on("response", (response) => {
          if (response.url().includes("/api/") && response.status() >= 400) {
            failedResponses.push(
              `${response.status()} ${response.request().method()} ${response.url()}`,
            );
          }
        });

        await page.goto(applicantPortalUrl);
        await login(page, fixture);
        await page.getByRole("link", { name: "Start application" }).first().click();
        await selectApplicationRoute(page, fixture.applicationTypeName);
        await selectOption(page, "Intake", fixture.intakeName);
        await Promise.all([
          page.waitForURL(`${applicantPortalUrl}/applications/**`),
          page
            .getByRole("button", { name: "Create draft", exact: true })
            .evaluate((element: HTMLElement) => element.click()),
        ]);

        const workspaceNavigator = page.getByRole("navigation", {
          name: "Application process",
        });
        await expect(
          workspaceNavigator.getByRole("button", {
            name: /Employment history/,
          }),
        ).toBeVisible();
        await expect(workspaceNavigator.getByRole("button", { name: /Referees/ })).toBeVisible();
        await expect(
          workspaceNavigator.getByRole("button", { name: /Personal details/ }),
        ).toHaveCount(0);
        await expect(page.getByLabel("First name")).not.toBeEditable();
        await expect(page.getByLabel("First name")).toHaveValue("Browser");
        await expect(page.getByLabel("Last name")).not.toBeEditable();
        await expect(page.getByLabel("Last name")).toHaveValue("Applicant");
        await uploadRequiredPersonalEvidence(page, testInfo, fixture.codeSuffix);

        const profileSave = page.waitForResponse(
          (response) =>
            response.url().endsWith("/profile") &&
            response.request().method() === "PUT" &&
            response.ok(),
        );
        await selectOption(page, "Title", "Mr");
        await page.getByLabel("Date of birth").fill("1994-04-12");
        await selectOptionUntilFieldContains(page, "Gender", "MALE", "MALE");
        await selectOptionUntilFieldContains(page, "Marital status", "SINGLE", "SINGLE");
        await page.getByLabel("National ID number").fill(`94-${fixture.codeSuffix}`);
        await selectOptionUntilFieldContains(
          page,
          "Country of residence",
          /ZW .*Zimbabwe/,
          "Zimbabwe",
        );
        await selectOptionUntilFieldContains(page, "Nationality", /ZW .*Zimbabwe/, "Zimbabwe");
        const activeSection = page.locator("#application-section-editor");
        await activeSection.getByLabel("Phone number").fill("+263772100001");
        await activeSection.getByLabel("Residential address").fill("630 Churchill Avenue, Harare");
        await profileSave;

        await clickVisibleButtonContaining(page, "Continue: Next of kin");
        await page.getByLabel("Full name").fill("Tariro Applicant");
        await selectOption(page, "Relationship", "PARENT");
        await page.getByLabel("Phone number").fill("+263772100002");
        await page.getByLabel("Email").fill(`kin-${fixture.codeSuffix.toLowerCase()}@example.test`);
        await page.getByLabel("Address").fill("Harare");
        await Promise.all([
          page.waitForResponse(
            (response) =>
              response.url().endsWith("/next-of-kin") &&
              response.request().method() === "POST" &&
              response.ok(),
          ),
          clickVisibleButtonContaining(page, "Save record"),
        ]);
        await expect(page.getByText("Tariro Applicant")).toBeVisible();

        await clickVisibleButtonContaining(page, "Continue: Qualifications");
        await completeSchoolQualification(
          page,
          testInfo,
          fixture,
          "University of Zimbabwe",
          `UZ-${routeScenario.route}`,
          `${routeScenario.route}${fixture.codeSuffix}`,
        );

        await clickVisibleButtonContaining(page, "Continue: Employment history");
        await expect(
          page.getByRole("heading", {
            name: "Employment history",
            exact: true,
          }),
        ).toBeVisible();
        await page.getByLabel("Employer").fill("UZ Business School");
        await page.getByLabel("Position").fill("Operations Manager");
        await page.getByLabel("Started on").fill("2020-01-15");
        await page.getByLabel("Ended on").fill("2025-12-31");
        await page
          .getByLabel("Responsibilities")
          .fill("Leading operational planning, people management, and service improvement.");
        await Promise.all([
          page.waitForResponse(
            (response) =>
              response.url().endsWith("/employment-history") &&
              response.request().method() === "POST" &&
              response.ok(),
          ),
          clickVisibleButtonContaining(page, "Save record"),
        ]);
        await expect(page.getByText("Operations Manager · UZ Business School")).toBeVisible();

        if (routeScenario.requiresMbaDeclarations) {
          await clickVisibleButtonContaining(page, "Continue: Previous UZ study");
          await expect(
            page.getByText("Previous University of Zimbabwe study", {
              exact: true,
            }),
          ).toBeVisible();
          await clickVisibleButtonContaining(page, "Save declaration");
          await dismissSuccessDialog(page);

          await clickVisibleButtonContaining(page, "Continue: Professional achievements");
          await page
            .getByLabel("I have no professional achievements to declare")
            .evaluate((element: HTMLElement) => element.click());
          await clickVisibleButtonContaining(page, "Save achievements");
          await dismissSuccessDialog(page);
        }

        await clickVisibleButtonContaining(page, "Continue: Referees");
        await expect(page.getByRole("heading", { name: "Referees", exact: true })).toBeVisible();
        await page.getByLabel("Title").fill("Dr");
        await page.getByLabel("Full name").fill("Tariro Dube");
        await page.getByLabel("Organisation").fill("University of Zimbabwe");
        await page.getByLabel("Position").fill("Dean");
        await page.getByLabel("Relationship to applicant").fill("Line manager");
        await page
          .getByLabel("Area of expertise")
          .fill("Academic leadership and postgraduate admissions");
        await page.getByLabel("Email").fill(fixture.firstRefereeEmail);
        await page.getByLabel("Phone number").fill("+263772100003");
        await Promise.all([
          page.waitForResponse(
            (response) =>
              response.url().endsWith("/referees") &&
              response.request().method() === "POST" &&
              response.ok(),
          ),
          clickVisibleButtonContaining(page, "Save record"),
        ]);
        await expect(page.getByText("Tariro Dube")).toBeVisible();
        await expect(page.getByText("Invitation sent").first()).toBeVisible();

        await page.getByLabel("Title").fill("Prof");
        await page.getByLabel("Full name").fill("Rutendo Moyo");
        await page.getByLabel("Organisation").fill("Graduate School of Management");
        await page.getByLabel("Position").fill("Programme Director");
        await page.getByLabel("Relationship to applicant").fill("Academic supervisor");
        await page
          .getByLabel("Area of expertise")
          .fill("Business management and executive education");
        await page.getByLabel("Email").fill(fixture.secondRefereeEmail);
        await page.getByLabel("Phone number").fill("+263772100004");
        await Promise.all([
          page.waitForResponse(
            (response) =>
              response.url().endsWith("/referees") &&
              response.request().method() === "POST" &&
              response.ok(),
          ),
          clickVisibleButtonContaining(page, "Save record"),
        ]);
        await expect(page.getByText("Rutendo Moyo")).toBeVisible();

        if (routeScenario.referenceCount === 3) {
          await page.getByLabel("Title").fill("Ms");
          await page.getByLabel("Full name").fill("Nyasha Sibanda");
          await page.getByLabel("Organisation").fill("Zimbabwe Institute of Management");
          await page.getByLabel("Position").fill("Executive Director");
          await page.getByLabel("Relationship to applicant").fill("Professional mentor");
          await page
            .getByLabel("Area of expertise")
            .fill("Executive leadership and professional development");
          await page.getByLabel("Email").fill(fixture.thirdRefereeEmail);
          await page.getByLabel("Phone number").fill("+263772100005");
          await Promise.all([
            page.waitForResponse(
              (response) =>
                response.url().endsWith("/referees") &&
                response.request().method() === "POST" &&
                response.ok(),
            ),
            clickVisibleButtonContaining(page, "Save record"),
          ]);
          await expect(page.getByText("Nyasha Sibanda")).toBeVisible();
        }

        const refereeEmails = [fixture.firstRefereeEmail, fixture.secondRefereeEmail];
        const refereeRelationships = ["Line manager", "Academic supervisor"];
        if (routeScenario.referenceCount === 3) {
          refereeEmails.push(fixture.thirdRefereeEmail);
          refereeRelationships.push("Professional mentor");
        }
        const invitations = await Promise.all(refereeEmails.map(waitForReferenceInvitation));
        for (const invitation of invitations) {
          expect(invitation.providerCode).toBeTruthy();
        }

        const refereeContexts = await Promise.all(invitations.map(() => browser.newContext()));
        try {
          await Promise.all(
            invitations.map(async (invitation, index) =>
              submitConfidentialReference(
                await refereeContexts[index]!.newPage(),
                invitation.responseUrl,
                refereeRelationships[index]!,
              ),
            ),
          );
        } finally {
          await Promise.all(refereeContexts.map((context) => context.close()));
        }

        await page.reload();
        await workspaceNavigator
          .getByRole("button", { name: /Referees/ })
          .evaluate((element: HTMLElement) => element.click());
        await expect(
          activeSection.locator(".rounded-lg").filter({ hasText: "Tariro Dube" }).first(),
        ).toContainText("Reference received");
        await expect(
          activeSection.locator(".rounded-lg").filter({ hasText: "Rutendo Moyo" }).first(),
        ).toContainText("Reference received");
        if (routeScenario.referenceCount === 3) {
          await expect(
            activeSection.locator(".rounded-lg").filter({ hasText: "Nyasha Sibanda" }).first(),
          ).toContainText("Reference received");
        }

        await clickVisibleButtonContaining(page, "Continue: Programme choices");
        await page
          .getByLabel("Programme choices")
          .evaluate((element: HTMLElement) => element.click());
        await page
          .getByRole("option", { name: new RegExp(fixture.programmeCode) })
          .click({ force: true });
        await page.keyboard.press("Escape");
        await clickVisibleButtonContaining(page, "Save choices");
        await dismissSuccessDialog(page);
        await expect(
          page
            .getByText(`${fixture.programmeCode} · ${routeScenario.programmeName}`, { exact: true })
            .last(),
        ).toBeVisible();

        await clickVisibleButtonContaining(page, "Continue: Review and declaration");
        await expect(
          page.getByRole("heading", {
            name: "Review and declaration",
            exact: true,
          }),
        ).toBeVisible();
        await expect(
          activeSection.getByText(fixture.applicationTypeName, { exact: true }),
        ).toBeVisible();
        await expect(
          activeSection.getByRole("heading", {
            name: "Employment history",
            exact: true,
          }),
        ).toBeVisible();
        if (routeScenario.requiresMbaDeclarations) {
          await expect(
            activeSection.getByRole("heading", {
              name: "Previous UZ study",
              exact: true,
            }),
          ).toBeVisible();
          await expect(
            activeSection.getByRole("heading", {
              name: "Professional achievements",
              exact: true,
            }),
          ).toBeVisible();
          await expect(
            activeSection.getByText("No previous UZ study declared.", {
              exact: true,
            }),
          ).toBeVisible();
          await expect(
            activeSection.getByText("No professional achievements declared.", {
              exact: true,
            }),
          ).toBeVisible();
        } else {
          await expect(
            activeSection.getByRole("heading", {
              name: "Previous UZ study",
              exact: true,
            }),
          ).toHaveCount(0);
          await expect(
            activeSection.getByRole("heading", {
              name: "Professional achievements",
              exact: true,
            }),
          ).toHaveCount(0);
        }
        await expect(
          activeSection.getByRole("heading", { name: "Referees", exact: true }),
        ).toBeVisible();
        await expect(
          activeSection.getByRole("heading", {
            name: `${fixture.programmeCode} · ${routeScenario.programmeName}`,
            exact: true,
          }),
        ).toBeVisible();

        await clickVisibleButtonContaining(page, "Accept declaration");
        await clickVisibleButtonContaining(page, "Accept declaration");
        await expect(page.getByText("Ready for submission")).toBeVisible();
        await clickVisibleButtonContaining(page, "Submit application");
        await clickVisibleButtonContaining(page, "Submit application");
        await expect(page.getByRole("heading", { name: "Application submitted" })).toBeVisible();
        await page
          .getByRole("button", { name: "OK" })
          .evaluate((element: HTMLElement) => element.click());
        await expect(page).toHaveURL(applicantPortalUrl + "/");
        await expect(page.getByRole("heading", { name: "Application submitted" })).toBeHidden();
        await page.waitForLoadState("networkidle");
        await expect(page.getByText(fixture.applicationTypeName, { exact: true })).toBeVisible();
        await expect(page.getByText("Before submission", { exact: true })).toHaveCount(0);
        await expect(page.getByText("Documents & fee", { exact: true })).toHaveClass(/font-medium/);

        await page.screenshot({
          path: testInfo.outputPath(
            `${routeScenario.route.toLowerCase()}-application-complete.png`,
          ),
          fullPage: true,
        });
        expect(failedResponses).toEqual([]);
        expect(consoleErrors).toEqual([]);
      } finally {
        await cleanupFixture(fixture);
      }
    });
  }
});
