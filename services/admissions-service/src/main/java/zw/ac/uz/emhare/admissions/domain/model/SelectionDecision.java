package zw.ac.uz.emhare.admissions.domain.model;

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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "selection_decisions")
public class SelectionDecision extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "selection_round_id", nullable = false)
  private SelectionRound selectionRound;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "programme_choice_id", nullable = false)
  private ApplicationProgrammeChoice programmeChoice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SelectionDecisionType decision;

  @Column(name = "rank_position")
  private Integer rankPosition;

  @Column(name = "quota_type_code", length = 50)
  private String quotaTypeCode;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Column(name = "decided_by_user_id", nullable = false)
  private UUID decidedByUserId;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  protected SelectionDecision() {}

  public SelectionDecision(
      SelectionRound selectionRound,
      ApplicationProgrammeChoice programmeChoice,
      SelectionDecisionType decision,
      Integer rankPosition,
      String quotaTypeCode,
      String reason,
      UUID decidedByUserId,
      Instant decidedAt) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A selection decision reason is required.");
    }
    if (rankPosition != null && rankPosition < 1) {
      throw new IllegalArgumentException("Selection rank position must be greater than zero.");
    }
    this.selectionRound = selectionRound;
    this.programmeChoice = programmeChoice;
    this.decision = decision;
    this.rankPosition = rankPosition;
    this.quotaTypeCode =
        quotaTypeCode == null || quotaTypeCode.isBlank() ? null : quotaTypeCode.trim();
    this.reason = reason.trim();
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = decidedAt;
  }

  public SelectionRound getSelectionRound() {
    return selectionRound;
  }

  public ApplicationProgrammeChoice getProgrammeChoice() {
    return programmeChoice;
  }

  public SelectionDecisionType getDecision() {
    return decision;
  }

  public Integer getRankPosition() {
    return rankPosition;
  }

  public String getQuotaTypeCode() {
    return quotaTypeCode;
  }

  public String getReason() {
    return reason;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
