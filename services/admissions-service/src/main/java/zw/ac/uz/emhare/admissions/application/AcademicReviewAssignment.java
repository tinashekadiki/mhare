package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable hierarchy-scope snapshot for an academic recommendation. @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_review_assignments")
@SQLRestriction("deleted_at IS NULL")
public class AcademicReviewAssignment extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selection_round_id", nullable = false)
    private SelectionRound selectionRound;
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
    private AcademicReviewAssignmentStatus status;
    @Column(name = "release_attempt", nullable = false)
    private int releaseAttempt;
    @Column(name = "released_by_user_id", nullable = false)
    private UUID releasedByUserId;
    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;
    @Column(name = "due_at")
    private Instant dueAt;
    @Column(name = "claimed_by_user_id")
    private UUID claimedByUserId;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "completed_by_user_id")
    private UUID completedByUserId;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected AcademicReviewAssignment() { }

    public AcademicReviewAssignment(
            SelectionRound selectionRound, ApplicationProgrammeChoice programmeChoice,
            UUID owningAcademicUnitId, String owningAcademicUnitCode, String owningAcademicUnitName,
            UUID recommendationAcademicUnitId, String recommendationAcademicUnitCode,
            String recommendationAcademicUnitName, String hierarchyPathJson,
            int releaseAttempt, UUID releasedByUserId, Instant releasedAt, Instant dueAt) {
        this.selectionRound = selectionRound;
        this.application = programmeChoice.getApplication();
        this.programmeChoice = programmeChoice;
        this.owningAcademicUnitId = owningAcademicUnitId;
        this.owningAcademicUnitCode = owningAcademicUnitCode;
        this.owningAcademicUnitName = owningAcademicUnitName;
        this.recommendationAcademicUnitId = recommendationAcademicUnitId;
        this.recommendationAcademicUnitCode = recommendationAcademicUnitCode;
        this.recommendationAcademicUnitName = recommendationAcademicUnitName;
        this.hierarchyPathJson = hierarchyPathJson;
        this.choiceRank = programmeChoice.getChoiceRank();
        this.status = AcademicReviewAssignmentStatus.OPEN;
        this.releaseAttempt = releaseAttempt;
        this.releasedByUserId = releasedByUserId;
        this.releasedAt = releasedAt;
        this.dueAt = dueAt;
    }

    public void claim(UUID actorUserId, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AcademicReviewAssignmentStatus.OPEN && status != AcademicReviewAssignmentStatus.RETURNED) {
            throw new IllegalStateException("Only an open or returned academic review can be claimed.");
        }
        status = AcademicReviewAssignmentStatus.CLAIMED;
        claimedByUserId = actorUserId;
        claimedAt = now;
    }

    public void markRecommended(UUID actorUserId, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AcademicReviewAssignmentStatus.CLAIMED || !actorUserId.equals(claimedByUserId)) {
            throw new IllegalStateException("The academic review must be claimed by the recommending staff member.");
        }
        status = AcademicReviewAssignmentStatus.RECOMMENDED;
        completedByUserId = actorUserId;
        completedAt = now;
    }

    public void returnForReconsideration() {
        if (status != AcademicReviewAssignmentStatus.RECOMMENDED) {
            throw new IllegalStateException("Only a recorded recommendation can be returned.");
        }
        status = AcademicReviewAssignmentStatus.RETURNED;
        claimedByUserId = null;
        claimedAt = null;
        completedByUserId = null;
        completedAt = null;
    }

    public void complete(UUID actorUserId, Instant now) {
        if (status != AcademicReviewAssignmentStatus.RECOMMENDED) {
            throw new IllegalStateException("Only a recorded recommendation can be completed.");
        }
        status = AcademicReviewAssignmentStatus.COMPLETED;
        completedByUserId = actorUserId;
        completedAt = now;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("Academic review changed. Refresh before retrying.");
    }

    public SelectionRound getSelectionRound() { return selectionRound; }
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
    public AcademicReviewAssignmentStatus getStatus() { return status; }
    public String getStatusCode() { return status.name(); }
    public int getReleaseAttempt() { return releaseAttempt; }
    public UUID getReleasedByUserId() { return releasedByUserId; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getDueAt() { return dueAt; }
    public UUID getClaimedByUserId() { return claimedByUserId; }
    public Instant getClaimedAt() { return claimedAt; }
}
