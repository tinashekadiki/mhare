package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeChoiceDecisionRepository extends JpaRepository<ProgrammeChoiceDecision, UUID> {
    Optional<ProgrammeChoiceDecision> findByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
    boolean existsByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
}
