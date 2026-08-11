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
