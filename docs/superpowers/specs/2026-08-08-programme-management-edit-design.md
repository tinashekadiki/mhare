# Programme Management: Edit Programme + Curriculum Version Retire E2E Coverage

Author: Tinashe K

## Purpose

Academic Setup → Programme Management (functional requirements 9.3, `FR-ACAD-020`–`FR-ACAD-024`) already supports creating and activating programmes, and creating/approving/retiring curriculum (programme) versions, in both the `academic-setup-service` backend and the `admin-portal` frontend, with E2E coverage in `tests/e2e/academic-setup.spec.ts`. Two gaps remain against the legacy baseline (`sparse-emhare/app/Controller/ProgrammesController.php`, which has full add/edit/view/delete CRUD) and against full FR coverage:

1. There is no way to edit an existing programme's details after creation — no backend endpoint, no frontend UI.
2. The "retire an approved curriculum version" capability is fully implemented (API + UI) but has no E2E test.

This spec covers closing both gaps.

## Scope

In scope:
- Editing a programme's programme type, programme level, name, award name, minimum/maximum duration, and legacy programme code, with a mandatory audited change reason.
- E2E coverage for editing a programme and for retiring an approved curriculum version.

Out of scope:
- Deleting a programme. The legacy `delete()` action is a hard delete with ~40 unguarded `hasMany` associations (no `dependent => true` anywhere) — a known anti-pattern, not something to replicate — and delete is not called for anywhere in FR 9.3.
- Retiring/deactivating a Programme itself (as opposed to a programme *version*). The `programmes.status` check constraint permits `RETIRED`, but no code path reaches it, and neither the FR document nor the legacy app models this. Treated as unused headroom, not a requirement.
- Module (FR 9.4) edit. Separate functional requirement subsection; not requested.

## Data Model

New migration `V5__add_programme_change_reason.sql` in `services/academic-setup-service/src/main/resources/db/migration/` (existing migrations `V1`–`V4` are never edited, per project convention):

- `ALTER TABLE programmes ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';`
- `ALTER TABLE programmes ADD CONSTRAINT ck_programmes_change_reason CHECK (length(trim(change_reason)) >= 10);`
- `ALTER TABLE programmes_aud ADD COLUMN change_reason varchar(1000);`

This mirrors exactly how `V3__add_governed_academic_calendar_edits.sql` added governed-edit support to `academic_years`, `academic_period_types`, `academic_periods`, and `intakes`.

## Backend (`services/academic-setup-service`)

- `Programme` entity: add `changeReason` field (set to `"Initial record creation."` in the constructor, matching `Intake`) and an `update(...)` method that:
  - Requires `expectedVersion` to match (optimistic locking, same as `activate()`).
  - Locks `code` and `owningAcademicUnit` once `status != DRAFT` — identical guard to `Intake.update()` locking `academicYear`/`code` once the intake leaves `DRAFT`. While `DRAFT`, all fields are editable.
  - Requires `changeReason` to be non-blank and ≥ 10 characters (same helper pattern as `Intake.requireChangeReason`).
  - Updates programme type, programme level, name, award name, min/max duration, legacy programme code.
- `AcademicSetupCommands.UpdateProgramme` record: `owningAcademicUnitId`, `programmeTypeId`, `programmeLevelId`, `name`, `awardName`, `minimumDurationPeriods`, `maximumDurationPeriods`, `legacyProgrammeCode`, `changeReason`, `expectedVersion` — validated the same way as `CreateProgramme`.
- `AcademicSetupService.updateProgramme(UUID programmeId, UpdateProgramme command)`: loads the programme, re-resolves type/level (and unit, for the DRAFT case), re-checks code uniqueness is unaffected (code itself doesn't change via this endpoint post-DRAFT; while DRAFT the owning unit can change but code remains this programme's own code so no uniqueness re-check is needed), calls `programme.update(...)`, returns the summary.
- `AcademicSetupController`: `PUT /api/academic/programmes/{programmeId}`, `@PreAuthorize(ACADEMIC_SETUP_ADMIN)`, same shape as `updateIntake`.

## Frontend (`apps/admin-portal/pages/operations/programmes.vue`)

- Extend `programmeForm` with `id`, `changeReason`, `expectedVersion` fields, following the `yearForm`/`intakeForm` pattern in `academic-calendar.vue`.
- Add an `editProgramme(programme)` function that opens the same drawer pre-filled, and a `saveProgramme()` that branches POST (create) vs PUT (update) based on `programmeForm.id`.
- Drawer title/description switch between "Create programme" and "Edit programme"; a "Change reason" `UTextarea` (`minlength="10"`) appears only when editing, matching the existing calendar screens exactly.
- Disable "Owning academic unit" and "Programme code" inputs when editing a non-DRAFT programme (leave enabled for DRAFT, matching the backend guard).
- Add an "Edit" row action (icon button, same as the calendar screens) next to the existing "Curriculum"/"Activate" actions.

## Testing

Extend `tests/e2e/academic-setup.spec.ts`'s existing CRUD test (`creates a Module, a programme, and its curriculum mapping end-to-end through the operator UI`) rather than adding a new test, since it already carries the fixtures this needs:

1. After the programme is created (still `DRAFT`), open its "Edit" drawer, change name/award name/duration, fill a change reason, save; assert the row reflects the new values and `programmes.change_reason` / the latest `programmes_aud` row both contain the submitted change reason (same assertion style already used for `intakes`/`intakes_aud` in the first test).
2. After "Approve and freeze" succeeds on the curriculum version, click "Retire version", supply a retirement date through the existing SweetAlert confirm flow (`useEmhareConfirm`), and assert the version's status becomes `RETIRED` in the UI.

No new fixture/cleanup logic should be needed beyond what `createCrudFixture`/`cleanupCrudFixture` already provide, since editing and retiring reuse the same created programme/version rows.

## Verification

- `mvn -pl services/academic-setup-service test` (or the project's equivalent) for the new backend unit/service coverage the change touches.
- `npx playwright test tests/e2e/academic-setup.spec.ts` for the extended E2E scenario.
