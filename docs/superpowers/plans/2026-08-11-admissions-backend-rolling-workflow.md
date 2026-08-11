# Admissions Backend — Rolling Workflow Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the core rolling-workflow engine in `services/admissions-service`: three new successor tables/entities (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`), automatic eligibility evaluation and academic-review creation triggered from the existing verification-confirmation flow, direct recommendation/decision commands, sequential programme-choice advancement, and retirement of the old selection-round/offer-batch/academic-review-assignment *write* paths — per ADR-0014 and the data model finalized in the Foundation plan (`docs/superpowers/plans/2026-08-10-admissions-rolling-workflow-foundation.md`).

**Architecture:** Additions clone the existing `AcademicReviewAssignment`/`AcademicUnitRecommendation`/`SelectionDecision` pattern (same base class, same audit/Envers conventions, same service/controller layering) but drop the `selection_round_id` scoping and the ranking/quota fields, per ADR-0014. This is a **hard cutover**: `ApplicationProgrammeChoice.choiceStatus` and `Application.status` change to the new enum values in this plan (not added alongside the old ones), and the old selection-round-decision and academic-review-assignment-claim/recommend/waitlist-release endpoints are removed in the same plan. The historical entities/tables (`SelectionRound`, `SelectionDecision`, `AcademicReviewAssignment`, `AcademicUnitRecommendation`, `OfferBatch`) and their own enum (`SelectionDecisionType`) are **not modified or deleted** — they remain as read-only history exactly as ADR-0014 requires; only their *write* endpoints are removed.

**Tech Stack:** Java 25 / Spring Boot 4 / Spring Data JPA / Hibernate Envers / PostgreSQL 18 / Flyway 13 / JUnit 5 / Mockito / AssertJ / Testcontainers.

## Global Constraints

- Never edit an existing migration file (V1–V33). This plan's migration is `V34__*.sql`, created fresh. Verified live via `mvn flyway:info` against `jdbc:postgresql://localhost:5433/emhare_admissions` on 2026-08-11: schema version 33, all 33 migrations applied successfully, no pending migrations.
- Every new business table needs: `id uuid PRIMARY KEY`, `created_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL`, `created_by_user_id uuid`, `modified_by_user_id uuid`, `deleted_at timestamptz`, `deleted_by_user_id uuid`, `version bigint NOT NULL`, and a matching `<table>_aud` table with `rev integer NOT NULL REFERENCES revinfo (rev)`, `revtype smallint`, composite `PRIMARY KEY (id, rev)`. Every new entity extends `zw.ac.uz.emhare.common.persistence.AuditableEntity` (`libraries/service-common`) and is annotated `@Audited @Entity @Table(...) @SQLRestriction("deleted_at IS NULL")`.
- All admissions-service enums live in one file: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsEnums.java`, as package-private (no `public` modifier) top-level `enum` declarations. New enums go in this file, not new files. Do not add `public` to any enum in this file — none of the existing ones have it.
- Do **not** modify `SelectionDecisionType`, `AcademicReviewAssignmentStatus`, `SelectionRoundStatus`, `OfferBatchStatus`, `OfferBatchScopeType`, `OfferType`, `OfferStatus` in `AdmissionsEnums.java`, and do not modify `SelectionRound`, `SelectionDecision`, `AcademicReviewAssignment`, `AcademicUnitRecommendation`, `OfferBatch`, or their repositories/tables. These are frozen, read-only history per ADR-0014.
- End every migration's `CREATE TABLE` block with a single `GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE <every new table and _aud table listed> TO emhare_service;` statement, matching the convention in every prior migration.
- Every mutating service method is `@Transactional`. Every service class uses constructor injection only — no field injection.
- Author every migration's header comment `-- Author: Tinashe K`. Author every new Java file's class Javadoc `@author Tinashe K` where the pattern file being cloned already has one (it does for every file cited in this plan).
- Local commits per task are expected (this plan is executed via superpowers:subagent-driven-development, whose review mechanism depends on one commit per task). No push, PR, or remote operation. Commit messages are plain — no `Co-Authored-By` or AI-attribution trailer (per this session's standing instruction).
- Run `mvn -pl services/admissions-service -am test` (or the project's `admissions:typecheck`-equivalent Maven test target) after every task that touches Java source, and paste the actual pass/fail summary into the task's commit-adjacent report — never claim tests pass without having run them.

## Source-of-Truth Reference (files to read before starting — every task below cites the exact ones it needs)

- `libraries/service-common/src/main/java/zw/ac/uz/emhare/common/persistence/AuditableEntity.java` — base class every new entity extends (full text quoted in Task 2 below).
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicReviewAssignment.java` and `AcademicUnitRecommendation.java` — the entities `AcademicReview`/`AcademicRecommendation` are cloned from.
- `services/admissions-service/src/main/resources/db/migration/V33__add_academic_unit_recommendation_workflow.sql` — the migration `V34` is cloned from (constraint naming, partial unique indexes, `_aud` table shape, grant statement).
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsEnums.java` — every enum this plan touches or adds lives here.
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/Application.java` and `ApplicationProgrammeChoice.java` — the two entities whose status enums and transition methods this plan changes.
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/ApplicationClearance.java` and `AdmissionsApplicationService.java` (method `moveToReview`, lines 213–248) — the exact hook point where automatic eligibility evaluation and academic-review creation attach.
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsSelectionOfferService.java` (method `recordEvaluation`, lines 349–413) and `QualificationEligibilityService.java` (method `evaluateRequirements`, returns `RequirementEvaluation(totalPoints, missingRequirements, missingRequirementEvidence, ruleEvidence)`) — the evaluation machinery this plan calls automatically instead of manually.
- `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsAcademicReviewService.java` and `web/AdmissionsAcademicReviewController.java` — the service/controller pair the new `academic-recommendation`/`decision`/`eligibility/recalculate` endpoints are modeled on, including the two-layer RBAC pattern (`security/AdmissionsRbacGuard.java` `@PreAuthorize` + service-layer `requireExactRootAssignment`).
- `services/admissions-service/src/test/java/zw/ac/uz/emhare/admissions/application/SelectionOfferMigrationTest.java` (raw-JDBC Testcontainers migration test), `AdmissionsAcademicReviewServiceTest.java` (Mockito service unit test), `web/AdmissionsApplicationControllerTest.java` (Mockito controller unit test, no `@WebMvcTest`) — the three test patterns this plan's tests follow.

## Confirmed enum specification (from the Foundation plan's data model doc and ADR-0014 — use these exact values, do not invent alternates)

```
academic_reviews.status:            OPEN, CLAIMED, RECOMMENDED, RETURNED, COMPLETED, CANCELLED
academic_recommendations.recommendation: RECOMMEND_ADMIT, RECOMMEND_REJECT
academic_recommendations.review_status:  PENDING, APPROVED, RETURNED, OVERRIDDEN   (reuse existing AcademicRecommendationReviewStatus — do not create a duplicate enum)
programme_choice_decisions.decision:     ADMIT, REJECT
```

**Deliberate deviation from the Foundation plan's literal enum text, noted for the record:** the Foundation plan's `choice_status` replacement text (`PENDING, ELIGIBLE, CONDITIONALLY_ELIGIBLE, INELIGIBLE, REQUIRES_REVIEW, UNDER_ACADEMIC_REVIEW, ADMITTED, REJECTED, OFFERED`) omits `CONVERTED`, which the live `ProgrammeChoiceStatus` enum has always had and `ApplicationProgrammeChoice.markConverted()` still needs — the Foundation plan's own list is missing a pre-existing terminal state it never intended to remove (it wasn't discussing applicant-to-student conversion at all). Task 3 below keeps `CONVERTED`.

---

### Task 1: Migration V34 — new tables, enum/constraint hard cutover with safe data backfill

**Files:**
- Create: `services/admissions-service/src/main/resources/db/migration/V34__add_rolling_admissions_workflow.sql`

**Interfaces:**
- Produces: the three new tables' exact column/constraint shape that Task 2's entities map onto field-for-field. Produces the new `application_programme_choices.choice_status` and `applications.status` CHECK-constraint value sets that Task 3's enum changes must match exactly.

- [ ] **Step 1: Write the migration**

```sql
-- Author: Tinashe K

-- Hard cutover: application_programme_choices.choice_status and applications.status move to the
-- rolling-workflow value set per ADR-0014. Any existing row already carrying a retired value is
-- backfilled to its nearest equivalent before the CHECK constraint is tightened, so this migration
-- is safe to run against a database that already has draft/dev data using the old values.

UPDATE application_programme_choices SET choice_status = 'ELIGIBLE' WHERE choice_status IN ('SHORTLISTED', 'WAITLISTED');
UPDATE application_programme_choices SET choice_status = 'ADMITTED' WHERE choice_status = 'SELECTED';

UPDATE applications SET status = 'ELIGIBLE' WHERE status = 'SHORTLISTED';
UPDATE applications SET status = 'ADMITTED' WHERE status = 'SELECTED';

ALTER TABLE application_programme_choices DROP CONSTRAINT IF EXISTS ck_application_programme_choice_status;
ALTER TABLE application_programme_choices ADD CONSTRAINT ck_application_programme_choice_status CHECK (
    choice_status IN ('PENDING', 'ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'INELIGIBLE', 'REQUIRES_REVIEW',
        'UNDER_ACADEMIC_REVIEW', 'ADMITTED', 'REJECTED', 'OFFERED', 'CONVERTED')
);

ALTER TABLE applications DROP CONSTRAINT IF EXISTS ck_applications_status;
ALTER TABLE applications ADD CONSTRAINT ck_applications_status CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'PAYMENT_PENDING', 'UNDER_REVIEW', 'INCOMPLETE', 'ELIGIBLE', 'NOT_ELIGIBLE',
        'UNDER_ACADEMIC_REVIEW', 'ADMITTED', 'REJECTED', 'OFFERED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'CONVERTED')
);

CREATE TABLE academic_reviews (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_code varchar(50) NOT NULL,
    owning_academic_unit_name varchar(180) NOT NULL,
    recommendation_academic_unit_id uuid NOT NULL,
    recommendation_academic_unit_code varchar(50) NOT NULL,
    recommendation_academic_unit_name varchar(180) NOT NULL,
    hierarchy_path_json jsonb NOT NULL,
    choice_rank integer NOT NULL,
    status varchar(30) NOT NULL,
    claimed_by_user_id uuid,
    claimed_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_review_application_choice UNIQUE (application_id, programme_choice_id),
    CONSTRAINT ck_academic_review_status CHECK
        (status IN ('OPEN', 'CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_academic_review_choice_rank CHECK (choice_rank > 0),
    CONSTRAINT ck_academic_review_claim CHECK (
        (status = 'OPEN' AND claimed_by_user_id IS NULL AND claimed_at IS NULL)
        OR (status IN ('CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED'))
    )
);

CREATE INDEX idx_academic_review_root_queue
    ON academic_reviews (recommendation_academic_unit_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE academic_recommendations (
    id uuid PRIMARY KEY,
    academic_review_id uuid NOT NULL REFERENCES academic_reviews (id),
    recommendation_sequence integer NOT NULL,
    recommendation varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    recommended_by_user_id uuid NOT NULL,
    recommended_at timestamptz NOT NULL,
    review_status varchar(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_recommendation_sequence UNIQUE (academic_review_id, recommendation_sequence),
    CONSTRAINT ck_academic_recommendation CHECK (recommendation IN ('RECOMMEND_ADMIT', 'RECOMMEND_REJECT')),
    CONSTRAINT ck_academic_recommendation_review_status CHECK
        (review_status IN ('PENDING', 'APPROVED', 'RETURNED', 'OVERRIDDEN')),
    CONSTRAINT ck_academic_recommendation_values CHECK
        (recommendation_sequence > 0 AND length(trim(reason)) > 0),
    CONSTRAINT ck_academic_recommendation_review CHECK (
        (review_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (review_status <> 'PENDING' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND length(trim(coalesce(review_reason, ''))) > 0)
    )
);

CREATE UNIQUE INDEX uk_academic_recommendation_pending
    ON academic_recommendations (academic_review_id)
    WHERE review_status = 'PENDING' AND deleted_at IS NULL;

CREATE TABLE programme_choice_decisions (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    decision varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    source_recommendation_id uuid REFERENCES academic_recommendations (id),
    decided_by_user_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_choice_decision_choice UNIQUE (programme_choice_id),
    CONSTRAINT ck_programme_choice_decision CHECK (decision IN ('ADMIT', 'REJECT')),
    CONSTRAINT ck_programme_choice_decision_reason CHECK (length(trim(reason)) > 0)
);

CREATE TABLE academic_reviews_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_choice_id uuid,
    owning_academic_unit_id uuid, owning_academic_unit_code varchar(50), owning_academic_unit_name varchar(180),
    recommendation_academic_unit_id uuid, recommendation_academic_unit_code varchar(50),
    recommendation_academic_unit_name varchar(180), hierarchy_path_json jsonb, choice_rank integer,
    status varchar(30), claimed_by_user_id uuid, claimed_at timestamptz, completed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE academic_recommendations_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    academic_review_id uuid, recommendation_sequence integer, recommendation varchar(30),
    reason varchar(1000), recommended_by_user_id uuid, recommended_at timestamptz, review_status varchar(30),
    reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE programme_choice_decisions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_choice_id uuid, decision varchar(30), reason varchar(1000),
    source_recommendation_id uuid, decided_by_user_id uuid, decided_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

COMMENT ON TABLE selection_rounds IS 'Historical — read-only from ADR-0014 onward. No new rows.';
COMMENT ON TABLE selection_decisions IS 'Historical — read-only from ADR-0014 onward. No new rows.';
COMMENT ON TABLE academic_review_assignments IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_reviews.';
COMMENT ON TABLE academic_unit_recommendations IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_recommendations.';
COMMENT ON TABLE offer_batches IS 'Historical — read-only from ADR-0014 onward. No new rows.';

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    academic_reviews, academic_recommendations, programme_choice_decisions,
    academic_reviews_aud, academic_recommendations_aud, programme_choice_decisions_aud
TO emhare_service;
```

Note on `ck_application_programme_choice_status` / `ck_applications_status` constraint names: these are **assumed names** following this codebase's `ck_<table>_<concern>` convention — verify the actual current constraint name first with `\d application_programme_choices` and `\d applications` in `psql`, or `SELECT conname FROM pg_constraint WHERE conrelid = 'application_programme_choices'::regclass AND contype = 'c';`, before writing the migration. The `DROP CONSTRAINT IF EXISTS` guards against a name mismatch causing a silent no-op, but the `ADD CONSTRAINT` must use a name that doesn't collide with the real existing one if `DROP` didn't match — resolve any mismatch by using the real name in both the `DROP` and `ADD`.

- [ ] **Step 2: Apply and verify against the live database**

```bash
cd services/admissions-service
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5433/emhare_admissions \
  -Dflyway.user=emhare_service \
  -Dflyway.password=emhare_dev_password
mvn flyway:info \
  -Dflyway.url=jdbc:postgresql://localhost:5433/emhare_admissions \
  -Dflyway.user=emhare_service \
  -Dflyway.password=emhare_dev_password
```

Expected: `Schema version: 34`, V34 shows `Success`, no pending migrations.

- [ ] **Step 3: Commit**

Stage only the new migration file. Plain commit message, no trailer.

---

### Task 2: New entities — AcademicReview, AcademicRecommendation, ProgrammeChoiceDecision

**Files:**
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicReview.java`
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicRecommendation.java`
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/ProgrammeChoiceDecision.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsEnums.java`

**Interfaces:**
- Consumes: Task 1's table/column shape; `AuditableEntity` (constructor pattern: protected no-arg constructor + a business constructor, matching `AcademicReviewAssignment`/`AcademicUnitRecommendation`).
- Produces: `AcademicReview`, `AcademicRecommendation`, `ProgrammeChoiceDecision` classes and their `AcademicReviewStatus`, `RecommendationOutcome`, `DecisionOutcome` enum values that Tasks 3, 5, 6, 7 construct and query.

- [ ] **Step 1: Add three enums to `AdmissionsEnums.java`**

Find this exact text:

```java
enum AcademicRecommendationReviewStatus {
    PENDING,
    APPROVED,
    RETURNED,
    OVERRIDDEN
}
```

Replace it with (adds three new enums immediately after the existing one, does not touch the existing one):

```java
enum AcademicRecommendationReviewStatus {
    PENDING,
    APPROVED,
    RETURNED,
    OVERRIDDEN
}

enum AcademicReviewStatus {
    OPEN,
    CLAIMED,
    RECOMMENDED,
    RETURNED,
    COMPLETED,
    CANCELLED
}

enum RecommendationOutcome {
    RECOMMEND_ADMIT,
    RECOMMEND_REJECT
}

enum DecisionOutcome {
    ADMIT,
    REJECT
}
```

- [ ] **Step 2: Write `AcademicReview.java`**

Clone the structure of `AcademicReviewAssignment.java` exactly (imports, `@Audited @Entity @Table(name = "academic_reviews") @SQLRestriction("deleted_at IS NULL")`, `@ManyToOne` to `Application` and `ApplicationProgrammeChoice`, `@JdbcTypeCode(SqlTypes.JSON)` for `hierarchyPathJson`), but drop `selectionRound`, `releaseAttempt`, `releasedByUserId`, `releasedAt`, `dueAt`, `completedByUserId` (none of these exist on the new table — `completedAt` alone remains, with no separate "completed by" actor column per the confirmed schema). Full source:

```java
package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Successor to {@link AcademicReviewAssignment} per ADR-0014: created automatically as soon as a
 * programme choice becomes eligible, scoped directly to the application and programme choice
 * instead of a selection round. @author Tinashe K
 */
@Audited
@Entity
@Table(name = "academic_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_review_application_choice",
                columnNames = {"application_id", "programme_choice_id"}))
@SQLRestriction("deleted_at IS NULL")
public class AcademicReview extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_choice_id", nullable = false)
    private ApplicationProgrammeChoice programmeChoice;
    @Column(name = "owning_academic_unit_id", nullable = false)
    private UUID owningAcademicUnitId;
    @Column(name = "owning_academic_unit_code", nullable = false, length = 50)
    private String owningAcademicUnitCode;
    @Column(name = "owning_academic_unit_name", nullable = false, length = 180)
    private String owningAcademicUnitName;
    @Column(name = "recommendation_academic_unit_id", nullable = false)
    private UUID recommendationAcademicUnitId;
    @Column(name = "recommendation_academic_unit_code", nullable = false, length = 50)
    private String recommendationAcademicUnitCode;
    @Column(name = "recommendation_academic_unit_name", nullable = false, length = 180)
    private String recommendationAcademicUnitName;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hierarchy_path_json", nullable = false, columnDefinition = "jsonb")
    private String hierarchyPathJson;
    @Column(name = "choice_rank", nullable = false)
    private int choiceRank;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcademicReviewStatus status;
    @Column(name = "claimed_by_user_id")
    private UUID claimedByUserId;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected AcademicReview() { }

    public AcademicReview(
            Application application, ApplicationProgrammeChoice programmeChoice,
            UUID owningAcademicUnitId, String owningAcademicUnitCode, String owningAcademicUnitName,
            UUID recommendationAcademicUnitId, String recommendationAcademicUnitCode,
            String recommendationAcademicUnitName, String hierarchyPathJson) {
        this.application = application;
        this.programmeChoice = programmeChoice;
        this.owningAcademicUnitId = owningAcademicUnitId;
        this.owningAcademicUnitCode = owningAcademicUnitCode;
        this.owningAcademicUnitName = owningAcademicUnitName;
        this.recommendationAcademicUnitId = recommendationAcademicUnitId;
        this.recommendationAcademicUnitCode = recommendationAcademicUnitCode;
        this.recommendationAcademicUnitName = recommendationAcademicUnitName;
        this.hierarchyPathJson = hierarchyPathJson;
        this.choiceRank = programmeChoice.getChoiceRank();
        this.status = AcademicReviewStatus.OPEN;
    }

    public void claim(UUID actorUserId, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AcademicReviewStatus.OPEN && status != AcademicReviewStatus.RETURNED) {
            throw new IllegalStateException("Only an open or returned academic review can be claimed.");
        }
        status = AcademicReviewStatus.CLAIMED;
        claimedByUserId = actorUserId;
        claimedAt = now;
    }

    public void markRecommended(UUID actorUserId, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AcademicReviewStatus.CLAIMED || !actorUserId.equals(claimedByUserId)) {
            throw new IllegalStateException("The academic review must be claimed by the recommending staff member.");
        }
        status = AcademicReviewStatus.RECOMMENDED;
    }

    public void returnForReconsideration() {
        if (status != AcademicReviewStatus.RECOMMENDED) {
            throw new IllegalStateException("Only a recorded recommendation can be returned.");
        }
        status = AcademicReviewStatus.RETURNED;
        claimedByUserId = null;
        claimedAt = null;
    }

    public void complete(Instant now) {
        if (status != AcademicReviewStatus.RECOMMENDED) {
            throw new IllegalStateException("Only a recorded recommendation can be completed.");
        }
        status = AcademicReviewStatus.COMPLETED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        if (status == AcademicReviewStatus.COMPLETED || status == AcademicReviewStatus.CANCELLED) {
            throw new IllegalStateException("Only an in-progress academic review can be cancelled.");
        }
        status = AcademicReviewStatus.CANCELLED;
        completedAt = now;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("Academic review changed. Refresh before retrying.");
    }

    public Application getApplication() { return application; }
    public ApplicationProgrammeChoice getProgrammeChoice() { return programmeChoice; }
    public UUID getOwningAcademicUnitId() { return owningAcademicUnitId; }
    public String getOwningAcademicUnitCode() { return owningAcademicUnitCode; }
    public String getOwningAcademicUnitName() { return owningAcademicUnitName; }
    public UUID getRecommendationAcademicUnitId() { return recommendationAcademicUnitId; }
    public String getRecommendationAcademicUnitCode() { return recommendationAcademicUnitCode; }
    public String getRecommendationAcademicUnitName() { return recommendationAcademicUnitName; }
    public String getHierarchyPathJson() { return hierarchyPathJson; }
    public int getChoiceRank() { return choiceRank; }
    public AcademicReviewStatus getStatus() { return status; }
    public String getStatusCode() { return status.name(); }
    public UUID getClaimedByUserId() { return claimedByUserId; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
```

- [ ] **Step 3: Write `AcademicRecommendation.java`**

Clone `AcademicUnitRecommendation.java`'s structure, dropping `rankPosition`, `quotaTypeCode`, and `finalDecision` (none exist on the new table — `programme_choice_decisions.source_recommendation_id` is the reverse link instead of a forward `final_decision_id`, per the Foundation plan's Fix 4 circular-FK removal). Reuse the existing `AcademicRecommendationReviewStatus` enum — do not create a new one.

```java
package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Successor to {@link AcademicUnitRecommendation} per ADR-0014: references {@link AcademicReview}
 * instead of {@link AcademicReviewAssignment}; ranking and quota fields are dropped because
 * ranking and quota category no longer factor into a recommendation. @author Tinashe K
 */
@Audited
@Entity
@Table(name = "academic_recommendations",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_recommendation_sequence",
                columnNames = {"academic_review_id", "recommendation_sequence"}))
@SQLRestriction("deleted_at IS NULL")
public class AcademicRecommendation extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_review_id", nullable = false)
    private AcademicReview academicReview;
    @Column(name = "recommendation_sequence", nullable = false)
    private int recommendationSequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationOutcome recommendation;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "recommended_by_user_id", nullable = false)
    private UUID recommendedByUserId;
    @Column(name = "recommended_at", nullable = false)
    private Instant recommendedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private AcademicRecommendationReviewStatus reviewStatus;
    @Column(name = "reviewed_by_user_id") private UUID reviewedByUserId;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason", length = 1000) private String reviewReason;

    protected AcademicRecommendation() { }

    public AcademicRecommendation(AcademicReview academicReview, int sequence,
            RecommendationOutcome recommendation, String reason, UUID actorUserId, Instant now) {
        this.academicReview = academicReview;
        this.recommendationSequence = sequence;
        this.recommendation = recommendation;
        this.reason = required(reason, "Recommendation reason");
        this.recommendedByUserId = actorUserId;
        this.recommendedAt = now;
        this.reviewStatus = AcademicRecommendationReviewStatus.PENDING;
    }

    public void approve(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.APPROVED, actorUserId, reason, now);
    }
    public void override(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.OVERRIDDEN, actorUserId, reason, now);
    }
    public void returnForReconsideration(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.RETURNED, actorUserId, reason, now);
    }
    private void review(AcademicRecommendationReviewStatus status, UUID actorUserId, String reason, Instant now) {
        if (reviewStatus != AcademicRecommendationReviewStatus.PENDING) {
            throw new IllegalStateException("Recommendation has already been reviewed.");
        }
        reviewStatus = status;
        reviewedByUserId = actorUserId;
        reviewedAt = now;
        reviewReason = required(reason, "Admissions review reason");
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    public AcademicReview getAcademicReview() { return academicReview; }
    public int getRecommendationSequence() { return recommendationSequence; }
    public RecommendationOutcome getRecommendation() { return recommendation; }
    public String getRecommendationCode() { return recommendation.name(); }
    public String getReason() { return reason; }
    public UUID getRecommendedByUserId() { return recommendedByUserId; }
    public Instant getRecommendedAt() { return recommendedAt; }
    public AcademicRecommendationReviewStatus getReviewStatus() { return reviewStatus; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
}
```

- [ ] **Step 4: Write `ProgrammeChoiceDecision.java`**

```java
package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Successor to {@link SelectionDecision} per ADR-0014: scoped directly to the application and
 * programme choice instead of a selection round, with only two outcomes (no shortlist, waitlist,
 * rank position, or quota type). Only an {@code ADMIT} decision can generate an offer.
 * @author Tinashe K
 */
@Audited
@Entity
@Table(name = "programme_choice_decisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_programme_choice_decision_choice",
                columnNames = "programme_choice_id"))
@SQLRestriction("deleted_at IS NULL")
public class ProgrammeChoiceDecision extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_choice_id", nullable = false)
    private ApplicationProgrammeChoice programmeChoice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DecisionOutcome decision;
    @Column(nullable = false, length = 1000)
    private String reason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_recommendation_id")
    private AcademicRecommendation sourceRecommendation;
    @Column(name = "decided_by_user_id", nullable = false)
    private UUID decidedByUserId;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ProgrammeChoiceDecision() { }

    public ProgrammeChoiceDecision(Application application, ApplicationProgrammeChoice programmeChoice,
            DecisionOutcome decision, String reason, AcademicRecommendation sourceRecommendation,
            UUID actorUserId, Instant now) {
        this.application = application;
        this.programmeChoice = programmeChoice;
        this.decision = decision;
        this.reason = required(reason);
        this.sourceRecommendation = sourceRecommendation;
        this.decidedByUserId = actorUserId;
        this.decidedAt = now;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A decision reason is required.");
        return value.trim();
    }
    public Application getApplication() { return application; }
    public ApplicationProgrammeChoice getProgrammeChoice() { return programmeChoice; }
    public DecisionOutcome getDecision() { return decision; }
    public String getReason() { return reason; }
    public AcademicRecommendation getSourceRecommendation() { return sourceRecommendation; }
    public UUID getDecidedByUserId() { return decidedByUserId; }
    public Instant getDecidedAt() { return decidedAt; }
}
```

- [ ] **Step 5: Compile**

```bash
mvn -pl services/admissions-service -am compile
```

Expected: `BUILD SUCCESS`. (No repositories exist for the new entities yet — that's Task 3 — so nothing references them yet; this step only confirms the entity classes themselves compile clean against the schema from Task 1.)

- [ ] **Step 6: Commit**

Stage the three new entity files and the enum addition. Plain commit message, no trailer.

---

### Task 3: Repositories + hard-cutover of `ApplicationProgrammeChoice` and `Application` status enums

**Files:**
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicReviewRepository.java`
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicRecommendationRepository.java`
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/ProgrammeChoiceDecisionRepository.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsEnums.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/ApplicationProgrammeChoice.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/Application.java`

**Interfaces:**
- Consumes: Task 2's entities and enums.
- Produces: `ApplicationProgrammeChoice.enterAcademicReview()`, `.recordDecision(DecisionOutcome, String)`, `.closeAfterHigherRankAdmission(String)` and `Application.enterAcademicReview(String)`, `.recordChoiceDecision(DecisionOutcome, String)` — the exact method names Task 6's service calls.

- [ ] **Step 1: Repositories** (plain `JpaRepository`, derived-query methods only, matching `AcademicReviewAssignmentRepository`'s style)

```java
// AcademicReviewRepository.java
package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicReviewRepository extends JpaRepository<AcademicReview, UUID> {
    Optional<AcademicReview> findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(UUID applicationId, UUID programmeChoiceId);
    java.util.List<AcademicReview> findAllByRecommendationAcademicUnitIdAndStatusAndDeletedAtIsNull(UUID recommendationAcademicUnitId, AcademicReviewStatus status);
}
```

```java
// AcademicRecommendationRepository.java
package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicRecommendationRepository extends JpaRepository<AcademicRecommendation, UUID> {
    Optional<AcademicRecommendation> findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(UUID academicReviewId, AcademicRecommendationReviewStatus reviewStatus);
    int countByAcademicReviewIdAndDeletedAtIsNull(UUID academicReviewId);
}
```

```java
// ProgrammeChoiceDecisionRepository.java
package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeChoiceDecisionRepository extends JpaRepository<ProgrammeChoiceDecision, UUID> {
    Optional<ProgrammeChoiceDecision> findByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
    boolean existsByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
}
```

- [ ] **Step 2: Hard-cutover `ProgrammeChoiceStatus` and `ApplicationStatus` in `AdmissionsEnums.java`**

Find this exact text:

```java
enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    PAYMENT_PENDING,
    UNDER_REVIEW,
    INCOMPLETE,
    ELIGIBLE,
    NOT_ELIGIBLE,
    SHORTLISTED,
    SELECTED,
    OFFERED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    CONVERTED
}
```

Replace it with:

```java
enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    PAYMENT_PENDING,
    UNDER_REVIEW,
    INCOMPLETE,
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNDER_ACADEMIC_REVIEW,
    ADMITTED,
    REJECTED,
    OFFERED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    CONVERTED
}
```

Find this exact text:

```java
enum ProgrammeChoiceStatus {
    PENDING,
    ELIGIBLE,
    INELIGIBLE,
    REQUIRES_REVIEW,
    SHORTLISTED,
    WAITLISTED,
    SELECTED,
    OFFERED,
    CONVERTED,
    REJECTED
}
```

Replace it with:

```java
enum ProgrammeChoiceStatus {
    PENDING,
    ELIGIBLE,
    CONDITIONALLY_ELIGIBLE,
    INELIGIBLE,
    REQUIRES_REVIEW,
    UNDER_ACADEMIC_REVIEW,
    ADMITTED,
    OFFERED,
    CONVERTED,
    REJECTED
}
```

- [ ] **Step 3: Update `ApplicationProgrammeChoice.java`**

Find this exact method:

```java
    public void recordEvaluation(EvaluationStatus evaluationStatus, String summary) {
        choiceStatus = switch (evaluationStatus) {
            case ELIGIBLE, CONDITIONALLY_ELIGIBLE -> ProgrammeChoiceStatus.ELIGIBLE;
            case NOT_ELIGIBLE -> ProgrammeChoiceStatus.INELIGIBLE;
            case REQUIRES_REVIEW -> ProgrammeChoiceStatus.REQUIRES_REVIEW;
        };
        evaluationSummary = summary;
    }

    public void recordSelectionDecision(SelectionDecisionType decision, String reason) {
        choiceStatus = switch (decision) {
            case SHORTLIST -> ProgrammeChoiceStatus.SHORTLISTED;
            case SELECT -> ProgrammeChoiceStatus.SELECTED;
            case REJECT -> ProgrammeChoiceStatus.REJECTED;
            case WAITLIST -> ProgrammeChoiceStatus.WAITLISTED;
        };
        decisionReason = reason;
    }

    public void closeAfterHigherRankSelection(String reason) {
        if (choiceStatus == ProgrammeChoiceStatus.ELIGIBLE
                || choiceStatus == ProgrammeChoiceStatus.SHORTLISTED
                || choiceStatus == ProgrammeChoiceStatus.WAITLISTED
                || choiceStatus == ProgrammeChoiceStatus.REQUIRES_REVIEW
                || choiceStatus == ProgrammeChoiceStatus.PENDING) {
            choiceStatus = ProgrammeChoiceStatus.REJECTED;
            decisionReason = reason;
        }
    }

    public void releaseWaitlist(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.WAITLISTED) {
            throw new IllegalStateException("Only a waitlisted programme choice can be released.");
        }
        choiceStatus = ProgrammeChoiceStatus.REJECTED;
        decisionReason = reason;
    }

    public void markOffered(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.SELECTED) {
            throw new IllegalStateException("Only a selected programme choice can receive an offer.");
        }
        choiceStatus = ProgrammeChoiceStatus.OFFERED;
        decisionReason = reason;
    }
```

Replace it with:

```java
    public void recordEvaluation(EvaluationStatus evaluationStatus, String summary) {
        choiceStatus = switch (evaluationStatus) {
            case ELIGIBLE -> ProgrammeChoiceStatus.ELIGIBLE;
            case CONDITIONALLY_ELIGIBLE -> ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE;
            case NOT_ELIGIBLE -> ProgrammeChoiceStatus.INELIGIBLE;
            case REQUIRES_REVIEW -> ProgrammeChoiceStatus.REQUIRES_REVIEW;
        };
        evaluationSummary = summary;
    }

    public void enterAcademicReview() {
        if (choiceStatus != ProgrammeChoiceStatus.ELIGIBLE && choiceStatus != ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE) {
            throw new IllegalStateException("Only an eligible programme choice can enter academic review.");
        }
        choiceStatus = ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW;
    }

    public void recordDecision(DecisionOutcome decision, String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW) {
            throw new IllegalStateException("Only a programme choice under academic review can receive an admission decision.");
        }
        choiceStatus = decision == DecisionOutcome.ADMIT ? ProgrammeChoiceStatus.ADMITTED : ProgrammeChoiceStatus.REJECTED;
        decisionReason = reason;
    }

    public void closeAfterHigherRankAdmission(String reason) {
        if (choiceStatus == ProgrammeChoiceStatus.ELIGIBLE
                || choiceStatus == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE
                || choiceStatus == ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW
                || choiceStatus == ProgrammeChoiceStatus.REQUIRES_REVIEW
                || choiceStatus == ProgrammeChoiceStatus.PENDING) {
            choiceStatus = ProgrammeChoiceStatus.REJECTED;
            decisionReason = reason;
        }
    }

    public void markOffered(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.ADMITTED) {
            throw new IllegalStateException("Only an admitted programme choice can receive an offer.");
        }
        choiceStatus = ProgrammeChoiceStatus.OFFERED;
        decisionReason = reason;
    }
```

(`recordSelectionDecision` and `releaseWaitlist` are deleted outright — no replacement — since `SHORTLIST`/`WAITLIST`/`SELECT` outcomes no longer exist on this entity. `SelectionDecisionType` itself is untouched, per Global Constraints; it's still used by the frozen `AcademicUnitRecommendation` entity.)

Find this exact method:

```java
    public void reopenAfterOfferClosed(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("Only an offered programme choice can return to selected status.");
        }
        choiceStatus = ProgrammeChoiceStatus.SELECTED;
        decisionReason = reason;
    }
```

Replace it with:

```java
    public void reopenAfterOfferClosed(String reason) {
        if (choiceStatus != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("Only an offered programme choice can return to admitted status.");
        }
        choiceStatus = ProgrammeChoiceStatus.ADMITTED;
        decisionReason = reason;
    }
```

- [ ] **Step 4: Update `Application.java`**

Find this exact block:

```java
    public void markSelected(String reason) {
        if (status != ApplicationStatus.ELIGIBLE && status != ApplicationStatus.SHORTLISTED) {
            throw new IllegalStateException("Only an eligible or shortlisted application can be selected.");
        }
        status = ApplicationStatus.SELECTED;
        statusReason = reason;
    }

    public void markShortlisted(String reason) {
        if (status != ApplicationStatus.ELIGIBLE) {
            throw new IllegalStateException("Only an eligible application can be shortlisted.");
        }
        status = ApplicationStatus.SHORTLISTED;
        statusReason = reason;
    }

    public void markOffered(String reason) {
        if (status != ApplicationStatus.SELECTED) {
            throw new IllegalStateException("Only a selected application can receive an offer.");
        }
        status = ApplicationStatus.OFFERED;
        statusReason = reason;
    }
```

Replace it with:

```java
    public void enterAcademicReview(String reason) {
        if (status != ApplicationStatus.ELIGIBLE) {
            throw new IllegalStateException("Only an eligible application can enter academic review.");
        }
        status = ApplicationStatus.UNDER_ACADEMIC_REVIEW;
        statusReason = reason;
    }

    public void recordChoiceDecision(DecisionOutcome decision, String reason) {
        if (status != ApplicationStatus.UNDER_ACADEMIC_REVIEW) {
            throw new IllegalStateException("Only an application under academic review can receive an admission decision.");
        }
        status = decision == DecisionOutcome.ADMIT ? ApplicationStatus.ADMITTED : ApplicationStatus.REJECTED;
        statusReason = reason;
    }

    public void markOffered(String reason) {
        if (status != ApplicationStatus.ADMITTED) {
            throw new IllegalStateException("Only an admitted application can receive an offer.");
        }
        status = ApplicationStatus.OFFERED;
        statusReason = reason;
    }
```

Find this exact method:

```java
    public void reopenAfterOfferClosed(String reason) {
        if (status != ApplicationStatus.OFFERED) {
            throw new IllegalStateException("Only an offered application can return to selected status.");
        }
        status = ApplicationStatus.SELECTED;
        statusReason = reason;
    }
```

Replace it with:

```java
    public void reopenAfterOfferClosed(String reason) {
        if (status != ApplicationStatus.OFFERED) {
            throw new IllegalStateException("Only an offered application can return to admitted status.");
        }
        status = ApplicationStatus.ADMITTED;
        statusReason = reason;
    }
```

- [ ] **Step 5: Compile and run the full admissions-service test suite**

```bash
mvn -pl services/admissions-service -am compile 2>&1 | tail -80
```

Expected: compile errors at every call site still referencing `markShortlisted`, `markSelected`, `recordSelectionDecision`, `releaseWaitlist`, `ProgrammeChoiceStatus.SHORTLISTED/SELECTED/WAITLISTED`, or `ApplicationStatus.SHORTLISTED/SELECTED`. **Do not fix these yet** — list every file:line the compiler reports and hand that list to Task 4 verbatim; Task 4 is exactly the task that resolves them by retiring the callers. Write the full compiler error list into this task's report.

- [ ] **Step 6: Commit**

This task will not compile clean on its own — that's expected and by design (Task 4 completes the cutover). Stage the 6 files listed above anyway and commit with a message that says compilation is intentionally broken pending Task 4, and lists the call sites Task 4 must resolve. Plain commit message, no trailer.

---

### Task 4: Retire old selection-round and academic-review-assignment write endpoints

**Files:**
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/web/AdmissionsSelectionOfferController.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsSelectionOfferService.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/web/AdmissionsAcademicReviewController.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsAcademicReviewService.java`
- Possibly modify: any other file the Task 3 Step 5 compiler-error list names.

**Interfaces:**
- Consumes: Task 3's compiler-error list (read that task's report before starting) and this plan's confirmed endpoint inventory below.
- Produces: a clean `mvn compile` — the exit criterion for this task.

**Confirmed endpoints to remove** (verified present by direct inspection before this plan was written — re-verify each still exists with the same path before deleting, since the codebase may have moved between plan-writing and execution):

From `AdmissionsSelectionOfferController` (`@RequestMapping("/api/admissions")`):
- `POST /selection-rounds/{id}/open`, `POST /selection-rounds/{id}/approve`, `POST /selection-rounds/{id}/close`
- `GET /selection-rounds/{id}/decisions` and whatever `POST` creates a `SelectionDecision` (search the controller for `SelectionDecision` usage — the exact endpoint path was not independently re-verified when this plan was written; find it via `grep -n "SelectionDecision" AdmissionsSelectionOfferController.java`)
- `POST /offer-batches/{id}/approve`, `POST /offer-batches/{id}/dispatch`, `POST /offer-batches/{id}/close`

From `AdmissionsAcademicReviewController` (`@RequestMapping("/api/admissions/academic-reviews")`):
- `POST /selection-rounds/{id}/releases`, `GET /selection-rounds/{id}/release-preview`
- `POST /{assignmentId}/claim`, `POST /{assignmentId}/recommendations`, `POST /{assignmentId}/review`, `POST /{assignmentId}/waitlist-release`

**Keep, do not remove:**
- `GET /selection-rounds` (list), `GET /offer-batches` (list), `GET /offers`, `GET /offers/mine`, and any other pure-read endpoint over the historical tables — these remain useful for the case-history view a later frontend plan will build. Only *write* (create/mutate) endpoints over the five frozen tables are removed.
- Everything in `AdmissionsSelectionOfferController` unrelated to selection rounds/offer batches (`recordEvaluation`, application-type CRUD, requirement-set CRUD, individual offer create/approve/dispatch/withdraw/expire/response, offer condition resolution) — **unchanged in this task**. Offer creation still reads `offer_batch_id` as optional/nullable today; this task does not touch offer-creation logic (that's a follow-up plan's job per ADR-0014's individual-offer-generation requirement — out of scope here).

- [ ] **Step 1: Remove the six controller endpoint groups listed above**, and their now-unused service methods in `AdmissionsSelectionOfferService`/`AdmissionsAcademicReviewService`. For each: read the method fully first, confirm nothing outside the removed group calls it (`grep -rn "methodName(" services/admissions-service/src/main/java/`), then delete the controller method and its service counterpart together. Leave the request DTO records in place if they're reused elsewhere; delete them only if they become unused (`grep` confirms zero remaining references).

- [ ] **Step 2: Resolve every compile error from Task 3 Step 5's list.** Most resolve automatically once the dead endpoints above are removed (they were the only callers). Any remaining reference to `SHORTLISTED`/`SELECTED`/`WAITLISTED`/`recordSelectionDecision`/`releaseWaitlist`/`markShortlisted`/`markSelected` outside `AdmissionsSelectionOfferController`/`Service` and `AdmissionsAcademicReviewController`/`Service` is a genuine gap this plan's authoring missed — stop and report it in the task report rather than guessing a fix; do not delete or rewrite unrelated business logic to force a compile.

- [ ] **Step 3: Compile and run tests**

```bash
mvn -pl services/admissions-service -am compile
mvn -pl services/admissions-service -am test 2>&1 | tail -100
```

Expected: `BUILD SUCCESS` on compile. Test run will likely show **failing or now-invalid tests** for the removed endpoints (e.g. any existing controller/service test exercising `claim`/`recommendations`/`review`/`waitlist-release`/selection-round decisions/offer-batch approval) — delete those specific test methods/classes (they test behavior that no longer exists) and re-run. Do not delete a failing test without confirming it tests only removed behavior; if a failing test also covers something still in scope, fix the test instead of deleting it.

- [ ] **Step 4: Commit**

Plain commit message summarizing which endpoints were removed and why (ADR-0014 hard cutover). No trailer.

---

### Task 5: Migration + entity test (raw JDBC Testcontainers)

**Files:**
- Create: `services/admissions-service/src/test/java/zw/ac/uz/emhare/admissions/application/RollingAdmissionsWorkflowMigrationTest.java`

**Interfaces:**
- Consumes: Task 1's migration, Task 3's constraint changes.
- Produces: nothing consumed by later tasks — this is a leaf verification task.

- [ ] **Step 1: Write the test**, following `SelectionOfferMigrationTest.java`'s exact pattern (`@Testcontainers`, static `PostgreSQLContainer`, `Flyway.configure()...migrate()` in `@BeforeAll`, per-test `Connection` with `setAutoCommit(false)` + `rollback()` in `@AfterEach`, hand-written `execute(sql, params...)` / `queryUuid(...)` helpers, asserting `SQLException.getSQLState()` — `"23514"` for CHECK violations, `"23505"` for unique violations). Read that file first for the exact helper method signatures to reuse; do not reinvent them.

Required test cases (write real JDBC assertions for each, not placeholders):
1. Inserting an `academic_reviews` row with a `status` value outside the six allowed values throws a `23514` CHECK violation.
2. Inserting two `academic_reviews` rows with the same `(application_id, programme_choice_id)` throws a `23505` unique violation.
3. Inserting an `academic_recommendations` row with `recommendation = 'SELECT'` (an old-model value) throws `23514`.
4. Inserting a second `PENDING`-review-status `academic_recommendations` row for the same `academic_review_id` while the first is still `PENDING` throws `23505` (the partial unique index).
5. Inserting two `programme_choice_decisions` rows for the same `programme_choice_id` throws `23505`.
6. Inserting an `application_programme_choices` row with `choice_status = 'SHORTLISTED'` throws `23514` (the retired value is now rejected).
7. Inserting an `application_programme_choices` row with `choice_status = 'CONDITIONALLY_ELIGIBLE'` succeeds (the new value is accepted).
8. Inserting an `applications` row with `status = 'UNDER_ACADEMIC_REVIEW'` succeeds.

- [ ] **Step 2: Run it**

```bash
mvn -pl services/admissions-service -am test -Dtest=RollingAdmissionsWorkflowMigrationTest
```

Expected: all 8 cases pass.

- [ ] **Step 3: Commit**

---

### Task 6: `AdmissionsRollingWorkflowService` — automatic eligibility evaluation, academic-review creation, recommendation, decision, sequential advancement

**Files:**
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsRollingWorkflowService.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AdmissionsApplicationService.java`

**Interfaces:**
- Consumes: `AdmissionsAcademicReviewService`'s hierarchy-resolution logic (read it first — the exact client call that resolves `owningAcademicUnitId`/`recommendationAcademicUnitId`/`hierarchyPathJson` from the programme, e.g. `AcademicSetupCatalogueClient.getProgrammeHierarchy(...)`, is in that service; call the same client the same way here rather than re-deriving the hierarchy resolution logic). Consumes `QualificationEligibilityService.evaluateRequirements(Application, AdmissionRequirementSet)`. Consumes Tasks 2/3's entities, repositories, and `ApplicationProgrammeChoice`/`Application` methods.
- Produces: `advanceApplication(UUID applicationId, UUID systemActorUserId)`, `recordRecommendation(UUID applicationId, UUID choiceId, RecommendationOutcome, String reason, UUID actorUserId)`, `recordDecision(UUID applicationId, UUID choiceId, DecisionOutcome, String reason, UUID actorUserId)`, `recalculateEligibility(UUID applicationId, UUID choiceId, UUID actorUserId)` — the four method signatures Task 7's controller calls directly.

- [ ] **Step 1: Read the four cited files in full** before writing code, to confirm nothing has moved since this plan was written: `AdmissionsAcademicReviewService.java`, `QualificationEligibilityService.java`, `AdmissionsApplicationService.java`, `AdmissionRequirementSetRepository.java`. This plan's code below already reflects their verified exact contents (`AcademicSetupCatalogueClient.getProgrammeHierarchy(UUID)` returning a `ProgrammeHierarchyResolution` with `.owningAcademicUnit()`/`.highestAcademicUnit()` each exposing `.id()`/`.code()`/`.name()`, and `.ancestorPath()`; `AdmissionRequirementSetRepository.findApprovedForRouteForUpdate(programmeId, applicationTypeId, admissionCycleId)` returning `List<AdmissionRequirementSet>`; `AdmissionRequirementSet.isApprovedAndEffectiveFor(programmeId, applicationTypeId, admissionCycleId, LocalDate)`; the `serialize(Object)`/`parseEnum(...)` private-helper pattern duplicated identically in both `AdmissionsSelectionOfferService` and `AdmissionsAcademicReviewService` — this codebase's established convention is each service keeps its own copy of these two small helpers rather than sharing them, so this new service does the same, not an extraction).

- [ ] **Step 2: Write `AdmissionsRollingWorkflowService`**

```java
package zw.ac.uz.emhare.admissions.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.ProgrammeHierarchyResolution;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

/**
 * Rolling per-applicant admissions engine per ADR-0014: automatic eligibility evaluation and
 * academic-review creation, direct academic recommendations and admission decisions, and
 * sequential programme-choice advancement. Replaces the manual selection-round release/claim flow
 * for all processing after ADR-0014. @author Tinashe K
 */
@Service
public class AdmissionsRollingWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    private final AdmissionRequirementSetRepository requirementSetRepository;
    private final ApplicationEvaluationRepository evaluationRepository;
    private final QualificationEligibilityService qualificationEligibilityService;
    private final AcademicReviewRepository academicReviewRepository;
    private final AcademicRecommendationRepository academicRecommendationRepository;
    private final ProgrammeChoiceDecisionRepository decisionRepository;
    private final AcademicSetupCatalogueClient academicSetupClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdmissionsRollingWorkflowService(
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository programmeChoiceRepository,
            AdmissionRequirementSetRepository requirementSetRepository,
            ApplicationEvaluationRepository evaluationRepository,
            QualificationEligibilityService qualificationEligibilityService,
            AcademicReviewRepository academicReviewRepository,
            AcademicRecommendationRepository academicRecommendationRepository,
            ProgrammeChoiceDecisionRepository decisionRepository,
            AcademicSetupCatalogueClient academicSetupClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.applicationRepository = applicationRepository;
        this.programmeChoiceRepository = programmeChoiceRepository;
        this.requirementSetRepository = requirementSetRepository;
        this.evaluationRepository = evaluationRepository;
        this.qualificationEligibilityService = qualificationEligibilityService;
        this.academicReviewRepository = academicReviewRepository;
        this.academicRecommendationRepository = academicRecommendationRepository;
        this.decisionRepository = decisionRepository;
        this.academicSetupClient = academicSetupClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Evaluates the highest-ranked unblocked pending programme choice and, if it becomes eligible,
     * creates its academic review. Called automatically after Admissions confirms application
     * clearance (see {@link AdmissionsApplicationService#moveToReview}) and after a REJECT decision
     * opens the next choice. Safe to call when there is no eligible next choice — becomes a no-op.
     */
    @Transactional
    public void advanceApplication(UUID applicationId, UUID systemActorUserId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        List<ApplicationProgrammeChoice> choices = programmeChoiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(applicationId);
        Optional<ApplicationProgrammeChoice> nextChoice = choices.stream()
                .filter(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.PENDING)
                .findFirst();
        if (nextChoice.isPresent()) {
            evaluateChoice(application, nextChoice.get(), systemActorUserId);
        }
        recomputeApplicationAggregateStatus(application, choices);
    }

    /**
     * Re-runs evaluation for one specific choice already sitting in {@code REQUIRES_REVIEW} or
     * {@code INELIGIBLE} (e.g. after an officer approves a requirement set that was previously
     * missing, or corrects application data). Unlike {@link #advanceApplication}, the target choice
     * is named explicitly rather than derived from rank, because a choice awaiting recalculation is
     * not the "next PENDING" choice.
     */
    @Transactional
    public void recalculateEligibility(UUID applicationId, UUID programmeChoiceId, UUID actorUserId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ApplicationProgrammeChoice choice = programmeChoiceRepository.findById(programmeChoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Programme choice not found."));
        if (choice.getChoiceStatus() != ProgrammeChoiceStatus.REQUIRES_REVIEW
                && choice.getChoiceStatus() != ProgrammeChoiceStatus.INELIGIBLE) {
            throw new IllegalStateException("Only a choice awaiting review or marked not eligible can be recalculated.");
        }
        evaluateChoice(application, choice, actorUserId);
        recomputeApplicationAggregateStatus(application,
                programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId));
    }

    private void evaluateChoice(Application application, ApplicationProgrammeChoice choice, UUID actorUserId) {
        List<AdmissionRequirementSet> candidates = requirementSetRepository.findApprovedForRouteForUpdate(
                choice.getProgrammeId(), application.getApplicationType().getId(), application.getAdmissionCycle().getId());
        Optional<AdmissionRequirementSet> requirementSet = candidates.stream()
                .filter(candidate -> candidate.isApprovedAndEffectiveFor(
                        choice.getProgrammeId(), application.getApplicationType().getId(),
                        application.getAdmissionCycle().getId(), LocalDate.now(clock)))
                .findFirst();
        if (requirementSet.isEmpty()) {
            choice.recordEvaluation(EvaluationStatus.REQUIRES_REVIEW,
                    "No approved requirement set is effective for this route; requires manual evaluation.");
            return;
        }
        if (evaluationRepository.existsByProgrammeChoiceIdAndRequirementSetIdAndDeletedAtIsNull(
                choice.getId(), requirementSet.get().getId())) {
            // Already evaluated against this exact requirement-set version; nothing changed since
            // the last automatic pass. Leave the choice's current status as-is.
            return;
        }

        QualificationEligibilityService.RequirementEvaluation evaluation =
                qualificationEligibilityService.evaluateRequirements(application, requirementSet.get());
        EvaluationStatus outcome = evaluation.missingRequirements().isEmpty()
                ? EvaluationStatus.ELIGIBLE
                : EvaluationStatus.NOT_ELIGIBLE;
        choice.recordEvaluation(outcome, outcome == EvaluationStatus.ELIGIBLE
                ? "Automatically evaluated as eligible."
                : "Automatically evaluated as not eligible: " + String.join(", ", evaluation.missingRequirements()));
        // Flush now, before any further mutation of `choice` in this same transaction. Without this,
        // Hibernate's dirty-checking coalesces this mutation with enterAcademicReview()'s mutation
        // below into a single UPDATE at end-of-transaction flush, so the database would see one jump
        // straight from the choice's pre-evaluation status to UNDER_ACADEMIC_REVIEW — a transition the
        // governance trigger (migration V34) does not and should not allow, since it validates one
        // legal step at a time. Flushing here makes each step its own UPDATE, matching the trigger's
        // one-step-at-a-time contract exactly.
        programmeChoiceRepository.saveAndFlush(choice);
        evaluationRepository.save(new ApplicationEvaluation(
                application, choice, requirementSet.get(), outcome,
                evaluation.totalPoints(), null,
                serialize(evaluation.missingRequirementEvidence()), serialize(evaluation.ruleEvidence()),
                clock.instant(), actorUserId));

        if (outcome == EvaluationStatus.ELIGIBLE) {
            createAcademicReview(application, choice);
        }
    }

    private void createAcademicReview(Application application, ApplicationProgrammeChoice choice) {
        ProgrammeHierarchyResolution hierarchy = academicSetupClient.getProgrammeHierarchy(choice.getProgrammeId());
        AcademicReview review = academicReviewRepository.save(new AcademicReview(application, choice,
                hierarchy.owningAcademicUnit().id(), hierarchy.owningAcademicUnit().code(), hierarchy.owningAcademicUnit().name(),
                hierarchy.highestAcademicUnit().id(), hierarchy.highestAcademicUnit().code(), hierarchy.highestAcademicUnit().name(),
                serialize(hierarchy.ancestorPath())));
        choice.enterAcademicReview();
        application.enterAcademicReview("Highest-ranked eligible choice entered academic review: " + review.getId());
    }

    @Transactional
    public AcademicRecommendation recordRecommendation(UUID applicationId, UUID programmeChoiceId,
            String outcomeCode, String reason, CoreCurrentUserProfile actorProfile) {
        RecommendationOutcome outcome = parseEnum(RecommendationOutcome.class, outcomeCode, "academic recommendation");
        AcademicReview review = academicReviewRepository
                .findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, programmeChoiceId)
                .orElseThrow(() -> new IllegalArgumentException("No academic review exists for this application and programme choice."));
        requireExactRootAssignment(actorProfile, review.getRecommendationAcademicUnitId());
        UUID actorUserId = actorProfile.user().id();
        Instant now = clock.instant();
        review.claim(actorUserId, now, review.getVersion());
        int sequence = academicRecommendationRepository.countByAcademicReviewIdAndDeletedAtIsNull(review.getId()) + 1;
        AcademicRecommendation recommendation = academicRecommendationRepository.save(
                new AcademicRecommendation(review, sequence, outcome, reason, actorUserId, now));
        review.markRecommended(actorUserId, review.getVersion());
        return recommendation;
    }

    @Transactional
    public ProgrammeChoiceDecision recordDecision(UUID applicationId, UUID programmeChoiceId,
            String decisionCode, String reason, UUID actorUserId) {
        DecisionOutcome outcome = parseEnum(DecisionOutcome.class, decisionCode, "admission decision");
        if (decisionRepository.existsByProgrammeChoiceIdAndDeletedAtIsNull(programmeChoiceId)) {
            throw new IllegalStateException("This programme choice already has an admission decision.");
        }
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ApplicationProgrammeChoice choice = programmeChoiceRepository.findById(programmeChoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Programme choice not found."));
        AcademicReview review = academicReviewRepository
                .findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, programmeChoiceId)
                .orElseThrow(() -> new IllegalArgumentException("No academic review exists for this application and programme choice."));
        AcademicRecommendation recommendation = academicRecommendationRepository
                .findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(review.getId(), AcademicRecommendationReviewStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("No pending recommendation to decide against."));

        Instant now = clock.instant();
        recommendation.approve(actorUserId, reason, now);
        review.complete(now);
        choice.recordDecision(outcome, reason);
        application.recordChoiceDecision(outcome, reason);
        ProgrammeChoiceDecision decision = decisionRepository.save(
                new ProgrammeChoiceDecision(application, choice, outcome, reason, recommendation, actorUserId, now));

        if (outcome == DecisionOutcome.ADMIT) {
            programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId).stream()
                    .filter(other -> other.getChoiceRank() > choice.getChoiceRank())
                    .forEach(other -> other.closeAfterHigherRankAdmission(
                            "Closed automatically: a higher-ranked choice was admitted."));
        } else {
            advanceApplication(applicationId, actorUserId);
        }
        return decision;
    }

    private void recomputeApplicationAggregateStatus(Application application, List<ApplicationProgrammeChoice> choices) {
        boolean anyEligible = choices.stream().anyMatch(item ->
                item.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.ADMITTED);
        boolean allFinal = choices.stream().allMatch(item ->
                item.getChoiceStatus() == ProgrammeChoiceStatus.INELIGIBLE
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.REJECTED);
        application.applyEvaluationOutcome(anyEligible, allFinal, "Automatic eligibility advancement.");
    }

    private List<UUID> qualifyingRootUnitIds(CoreCurrentUserProfile profile) {
        if (profile == null || profile.user() == null || !"ACTIVE".equals(profile.user().status())
                || profile.roleAssignments() == null) {
            return List.of();
        }
        return profile.roleAssignments().stream()
                .filter(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode()))
                .map(role -> role.academicUnitId()).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private void requireExactRootAssignment(CoreCurrentUserProfile profile, UUID rootUnitId) {
        if (!qualifyingRootUnitIds(profile).contains(rootUnitId)) {
            throw new AccessDeniedException(
                    "An active Academic Unit Staff assignment at the exact highest academic unit is required.");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Evaluation evidence could not be serialized.", exception);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String label) {
        try {
            return Enum.valueOf(enumType, requiredText(value, label).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, exception);
        }
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}
```

Note on `review.claim(...)` inside `recordRecommendation`: the academic review starts `OPEN` (never `CLAIMED`) since this plan's automatic creation path has no separate "claim" step — the recommending officer's own `recordRecommendation` call claims and immediately records in one action, collapsing `AdmissionsAcademicReviewService`'s two-step `claim()` then `recommend()` flow into one, since there is no longer a shared queue of assignments multiple officers compete to claim first (FR-SEL-029: recommendation authority belongs to *every* active staff member at the unit, and nothing in ADR-0014 requires a separate claim step for the new flow). If two officers race to recommend on the same review simultaneously, the second's `review.markRecommended(...)` call throws `IllegalStateException` (status is no longer `CLAIMED` by them) — this is the correct, existing optimistic-conflict behavior, not a gap.

- [ ] **Step 3: Wire the automatic trigger into `AdmissionsApplicationService.moveToReview`**

Find this exact text:

```java
        ApplicationStatus fromStatus = application.getStatus();
        application.moveToUnderReview(actorUserId, reason);
        clearanceRepository.save(new ApplicationClearance(application, actorUserId, reason, clock.instant()));
        statusEventRepository.save(new ApplicationStatusEvent(application, fromStatus, application.getStatus(), reason, actorUserId));
        integrationOutboxService.enqueueVerificationDecisionNotification(application);
        return summary(application, findPaymentReference(application.getId()));
    }
```

Replace it with:

```java
        ApplicationStatus fromStatus = application.getStatus();
        application.moveToUnderReview(actorUserId, reason);
        clearanceRepository.save(new ApplicationClearance(application, actorUserId, reason, clock.instant()));
        statusEventRepository.save(new ApplicationStatusEvent(application, fromStatus, application.getStatus(), reason, actorUserId));
        integrationOutboxService.enqueueVerificationDecisionNotification(application);
        rollingWorkflowService.advanceApplication(application.getId(), actorUserId);
        return summary(application, findPaymentReference(application.getId()));
    }
```

Add `AdmissionsRollingWorkflowService rollingWorkflowService` as a new constructor-injected field on `AdmissionsApplicationService` (find its constructor, add the parameter and the `this.rollingWorkflowService = rollingWorkflowService;` assignment following the exact style of the other constructor-injected fields already there).

- [ ] **Step 4: Compile, then run the full admissions-service suite**

```bash
mvn -pl services/admissions-service -am compile
mvn -pl services/admissions-service -am test 2>&1 | tail -150
```

Resolve every compile error and every newly-failing test before proceeding — do not commit red. Paste the final green summary into the task report.

- [ ] **Step 5: Commit**

---

### Task 7: Summary DTOs + controller endpoints — academic-recommendation, decision, eligibility/recalculate

**Files:**
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/AcademicRecommendationSummary.java`
- Create: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/application/ProgrammeChoiceDecisionSummary.java`
- Modify: `services/admissions-service/src/main/java/zw/ac/uz/emhare/admissions/web/AdmissionsSelectionOfferController.java`

**Interfaces:**
- Consumes: Task 6's `AdmissionsRollingWorkflowService.recordRecommendation(UUID, UUID, String, String, CoreCurrentUserProfile)`, `.recordDecision(UUID, UUID, String, String, UUID)`, `.recalculateEligibility(UUID, UUID, UUID)` (raw `String` outcome/decision codes — the service parses them internally via its own private `parseEnum`, matching `AdmissionsSelectionOfferService.recordEvaluation`'s existing convention of taking a raw status string from the DTO rather than a pre-parsed enum).
- Produces: `POST /api/admissions/applications/{applicationId}/choices/{programmeChoiceId}/academic-recommendation`, `POST /api/admissions/applications/{applicationId}/choices/{programmeChoiceId}/decision`, `POST /api/admissions/applications/{applicationId}/choices/{programmeChoiceId}/eligibility/recalculate` — matching the exact path style of this controller's existing `POST /applications/{applicationId}/choices/{programmeChoiceId}/evaluations` endpoint (same controller, same nesting convention).

- [ ] **Step 1: Write the two summary DTOs**, following `EvaluationSummary`'s exact convention (public record, package-private static `from(entity)` factory):

```java
// AcademicRecommendationSummary.java
package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AcademicRecommendationSummary(
        UUID id, UUID academicReviewId, int recommendationSequence,
        String recommendation, String reason, UUID recommendedByUserId, Instant recommendedAt,
        String reviewStatus) {
    static AcademicRecommendationSummary from(AcademicRecommendation value) {
        return new AcademicRecommendationSummary(
                value.getId(), value.getAcademicReview().getId(), value.getRecommendationSequence(),
                value.getRecommendationCode(), value.getReason(), value.getRecommendedByUserId(), value.getRecommendedAt(),
                value.getReviewStatus().name());
    }
}
```

```java
// ProgrammeChoiceDecisionSummary.java
package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record ProgrammeChoiceDecisionSummary(
        UUID id, UUID applicationId, UUID programmeChoiceId,
        String decision, String reason, UUID decidedByUserId, Instant decidedAt) {
    static ProgrammeChoiceDecisionSummary from(ProgrammeChoiceDecision value) {
        return new ProgrammeChoiceDecisionSummary(
                value.getId(), value.getApplication().getId(), value.getProgrammeChoice().getId(),
                value.getDecision().name(), value.getReason(), value.getDecidedByUserId(), value.getDecidedAt());
    }
}
```

`ProgrammeChoiceDecision` and `AcademicRecommendation` need public getters for every field the `from(...)` factories above read — confirm each getter listed exists after Task 2 (it does, per that task's entity source) before using it here.

- [ ] **Step 2: Read the controller's existing `recordEvaluation` endpoint method in full** (exact `@PostMapping` path, `@PreAuthorize` string, how it resolves the actor via `CoreIdentityClient.syncCurrentUser(authentication)`, and its exact request-DTO record location/name) to match its style precisely — the code below assumes that pattern; adjust only if the real file differs from what this plan's earlier source survey recorded.

- [ ] **Step 3: Add two request DTO records** (`eligibility/recalculate` takes no body):

```java
public record RecordRecommendationRequest(@NotBlank String recommendation, @NotBlank String reason) { }
public record RecordDecisionRequest(@NotBlank String decision, @NotBlank String reason) { }
```

Place them in whichever existing nested-requests container class this controller already uses (matching `AdmissionsAcademicReviewController`'s `AcademicReviewRequests` naming pattern) — do not create a new top-level requests class if this controller already has one.

- [ ] **Step 4: Add three endpoint methods**

```java
@PostMapping("/applications/{applicationId}/choices/{programmeChoiceId}/academic-recommendation")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND')")
public AcademicRecommendationSummary recordRecommendation(
        @PathVariable UUID applicationId, @PathVariable UUID programmeChoiceId,
        @Valid @RequestBody RecordRecommendationRequest request, Authentication authentication) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    return AcademicRecommendationSummary.from(rollingWorkflowService.recordRecommendation(
            applicationId, programmeChoiceId, request.recommendation(), request.reason(), profile));
}

@PostMapping("/applications/{applicationId}/choices/{programmeChoiceId}/decision")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
public ProgrammeChoiceDecisionSummary recordDecision(
        @PathVariable UUID applicationId, @PathVariable UUID programmeChoiceId,
        @Valid @RequestBody RecordDecisionRequest request, Authentication authentication) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    return ProgrammeChoiceDecisionSummary.from(rollingWorkflowService.recordDecision(
            applicationId, programmeChoiceId, request.decision(), request.reason(), profile.user().id()));
}

@PostMapping("/applications/{applicationId}/choices/{programmeChoiceId}/eligibility/recalculate")
@PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
public void recalculateEligibility(
        @PathVariable UUID applicationId, @PathVariable UUID programmeChoiceId, Authentication authentication) {
    CoreCurrentUserProfile profile = coreIdentityClient.syncCurrentUser(authentication);
    rollingWorkflowService.recalculateEligibility(applicationId, programmeChoiceId, profile.user().id());
}
```

Match the exact `CoreIdentityClient` field name and `syncCurrentUser(...)` call already present in this controller (`coreIdentityClient` above is this plan's best-guess field name from the earlier source survey — use the real field name found in Step 2 if it differs). The permission strings `ADMISSIONS_ACADEMIC_UNIT_RECOMMEND` and `ADMISSIONS_APPLICATION_REVIEW` are taken from this plan's earlier source survey of `AdmissionsRbacGuard`/this controller — verify both still exist with those exact names in `AdmissionsRbacGuard.java` before using them; if either has a different exact string, use the real one and note the correction in the task report.

Add `AdmissionsRollingWorkflowService rollingWorkflowService` as a new constructor-injected field on this controller, following its existing constructor's style.

- [ ] **Step 5: Compile, run tests, exercise manually**

```bash
mvn -pl services/admissions-service -am compile
mvn -pl services/admissions-service -am test
```

If a local instance can be started against the already-running dev Postgres (`mvn -pl services/admissions-service spring-boot:run`, or however this service is normally started for manual checks — check `README.md`/`Makefile` for the actual command), exercise all three new endpoints with `curl` against a seeded application/choice and confirm each returns 200 with a sensible body, and that calling `decision` twice on the same choice returns a 4xx (the `existsByProgrammeChoiceIdAndDeletedAtIsNull` guard). Paste the actual request/response pairs into the task report — do not claim this was checked without pasted evidence.

- [ ] **Step 6: Commit**

---

### Task 8: Service unit tests (Mockito) for `AdmissionsRollingWorkflowService`

**Files:**
- Create: `services/admissions-service/src/test/java/zw/ac/uz/emhare/admissions/application/AdmissionsRollingWorkflowServiceTest.java`

**Interfaces:**
- Consumes: Task 6's finalized service (read its actual final method signatures after Task 6's known-gaps are closed, not this plan's draft).

- [ ] **Step 1: Write the test**, following `AdmissionsAcademicReviewServiceTest.java`'s exact pattern: `@ExtendWith(MockitoExtension.class)`, `@Mock` for every repository/client dependency, service constructed directly in `@BeforeEach` with `Clock.fixed(...)`, AssertJ `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` for failure cases and plain `assertThat(...)` for success cases. Read that file first for the exact mock-setup idiom (likely `given(mockRepo.findById(id)).willReturn(Optional.of(entity))` via BDDMockito, given the codebase's existing style — confirm from the file itself rather than assuming Mockito's plain `when(...)`).

Required test cases (real assertions, not placeholders):
1. `advanceApplication` on an application whose top-ranked choice is `PENDING` and evaluates eligible creates an `AcademicReview`, moves the choice to `UNDER_ACADEMIC_REVIEW`, and moves the application to `UNDER_ACADEMIC_REVIEW`.
2. `advanceApplication` when no requirement set is approved/effective for the choice sets the choice to `REQUIRES_REVIEW` and does not create an `AcademicReview`.
3. `advanceApplication` on an application with no `PENDING` choices is a no-op (verify zero interactions with `academicReviewRepository.save`).
4. `recordRecommendation` throws when no `AcademicReview` exists for the given application/choice pair.
5. `recordDecision` with `ADMIT` closes every lower-ranked choice via `closeAfterHigherRankAdmission` (verify each lower choice's mock/spy received the call, or verify via a real in-memory `ApplicationProgrammeChoice` instance's resulting `choiceStatus`).
6. `recordDecision` with `REJECT` calls `advanceApplication` to open the next eligible choice (verify the next `PENDING` choice's evaluation path runs — this may require the test to use two real (not mocked) `ApplicationProgrammeChoice` instances at different ranks, constructed via their real constructors, since `advanceApplication`'s ranking logic operates on real entity state, not a mock).
7. `recordDecision` throws `IllegalStateException` when a `ProgrammeChoiceDecision` already exists for the choice (the `existsByProgrammeChoiceIdAndDeletedAtIsNull` guard).

- [ ] **Step 2: Run it**

```bash
mvn -pl services/admissions-service -am test -Dtest=AdmissionsRollingWorkflowServiceTest
```

Expected: all 7 cases pass.

- [ ] **Step 3: Commit**

---

### Task 9: Controller unit tests for the three new endpoints

**Files:**
- Modify or extend the existing test class covering `AdmissionsSelectionOfferController` (find it — likely `AdmissionsSelectionOfferControllerTest.java`; if it doesn't exist, create it following `AdmissionsApplicationControllerTest.java`'s pattern: controller instantiated directly with mocked service/client dependencies, no `@WebMvcTest`, no `MockMvc`).

**Interfaces:**
- Consumes: Task 7's finalized controller.

- [ ] **Step 1: Write three test cases**, one per new endpoint: a happy-path call verifying the mocked `AdmissionsRollingWorkflowService` method was invoked with the correctly-parsed arguments (path variables + parsed enum + resolved actor), and confirm the `@PreAuthorize` SpEL string is present on each method via reflection or a simple annotation-presence assertion if this test class's existing pattern already does that for other endpoints (check first — do not invent a new verification style if one already exists in this file).

- [ ] **Step 2: Run it**

```bash
mvn -pl services/admissions-service -am test
```

Expected: full green suite, no regressions anywhere else in `admissions-service`.

- [ ] **Step 3: Commit**

---

## What This Plan Deliberately Does Not Do

- No `GET /api/admissions/work-items` or `GET /api/admissions/work-items/{applicationId}` read API — deferred to a follow-up plan once this engine exists to read from.
- No individual-offer-generation trigger from an `ADMIT` decision (FR-OFFER-001's "generate one offer directly... without an offer batch") — deferred; `AdmissionOffer` **entity and endpoint code** is untouched by this plan. **However, discovered during Task 4 (not anticipated when this plan was written): the pre-existing `validate_offer_source()` trigger (`V15__make_offer_source_snapshot_immutable.sql`) makes offer creation completely non-functional after this plan's hard cutover, not merely "not yet automated."** That trigger's `INSERT` path requires a `selection_decisions` row with `decision = 'SELECT'` inside an `APPROVED` `selection_round`, joined to an `APPROVED` `offer_batches` row matching `NEW.offer_batch_id`, and separately requires `application_programme_choices.choice_status = 'SELECTED'` and `applications.status = 'SELECTED'` — every one of those is now either a retired enum value that can never be written again, or a table this plan's ADR-0014 cutover stops populating. The old "staff manually creates an individual offer" pathway that Task 4 correctly left in place therefore cannot succeed against any application processed through the new rolling workflow. This is not a Task 4 defect — Task 4's own scope (remove write endpoints over the five frozen tables, leave offer-creation code alone) was followed correctly; the trigger was a pre-existing landmine this plan's cutover detonated. **Fixing `validate_offer_source()` for the new ADMIT-based, batch-less model is real work belonging to the deferred individual-offer-generation follow-up plan, not a quick patch here** — a proper fix needs to replace the whole INSERT validation query (lines 45–87 of V15), not just swap one literal, and doing that without also building the "generate one offer directly from an `ADMIT` decision" feature would leave incoherent half-migrated validation logic. Until that follow-up plan lands, **Admissions staff cannot create any new offer for an application processed through this plan's rolling workflow** — flag this loudly and treat it as a priority-ordering signal for which follow-up plan comes next.
- No admin-portal or applicant-portal changes. The seven admin-portal pages this plan's backend cutover breaks (`admissions-selection.vue`, `admissions-academic-release.vue`, `admissions-recommendations.vue`, `admissions-decisions.vue`, and any page calling the offer-batch write endpoints) are **expected to fail** against this backend until a frontend plan retires/redirects them — this was the explicit, acknowledged cost of the "hard cutover now" choice. Flag this loudly to whoever picks up the frontend plan next.
- No Documents or Notifications service changes.
- No changes to `SelectionDecisionType`, `AcademicReviewAssignmentStatus`, `SelectionRoundStatus`, `OfferBatchStatus`, `OfferBatchScopeType`, or the five frozen historical tables/entities.
