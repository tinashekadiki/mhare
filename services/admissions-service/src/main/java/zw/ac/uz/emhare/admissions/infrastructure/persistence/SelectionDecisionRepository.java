package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.SelectionDecision;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface SelectionDecisionRepository extends JpaRepository<SelectionDecision, UUID> {
    long countBySelectionRoundIdAndDeletedAtIsNull(UUID selectionRoundId);
    Optional<SelectionDecision> findBySelectionRoundIdAndProgrammeChoiceIdAndDeletedAtIsNull(UUID roundId, UUID choiceId);
    List<SelectionDecision> findAllBySelectionRoundIdAndDeletedAtIsNullOrderByRankPositionAsc(UUID roundId);
    List<SelectionDecision> findAllByProgrammeChoiceApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(
            UUID applicationId);

    @Query("""
            select decision
            from SelectionDecision decision
            where decision.programmeChoice.id = :choiceId
              and decision.decision = zw.ac.uz.emhare.admissions.domain.model.SelectionDecisionType.SELECT
              and decision.selectionRound.status = zw.ac.uz.emhare.admissions.domain.model.SelectionRoundStatus.APPROVED
              and decision.deletedAt is null
            """)
    Optional<SelectionDecision> findApprovedSelectionForChoice(@Param("choiceId") UUID choiceId);

    @Query("""
            select count(decision)
            from SelectionDecision decision
            where decision.programmeChoice.application.id = :applicationId
              and decision.programmeChoice.id <> :choiceId
              and decision.decision = zw.ac.uz.emhare.admissions.domain.model.SelectionDecisionType.SELECT
              and decision.deletedAt is null
            """)
    long countOtherSelectionsForApplication(
            @Param("applicationId") UUID applicationId,
            @Param("choiceId") UUID choiceId);
}
