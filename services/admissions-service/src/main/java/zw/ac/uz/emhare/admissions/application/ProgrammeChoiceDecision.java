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
