package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceDecision;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeChoiceDecisionRepository extends JpaRepository<ProgrammeChoiceDecision, UUID> {
    Optional<ProgrammeChoiceDecision> findByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
    boolean existsByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
    java.util.List<ProgrammeChoiceDecision> findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(UUID applicationId);
}
