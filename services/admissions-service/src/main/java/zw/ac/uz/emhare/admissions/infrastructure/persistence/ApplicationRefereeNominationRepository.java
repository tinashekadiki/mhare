package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationRefereeNomination;

/** @author Tinashe K */
public interface ApplicationRefereeNominationRepository extends JpaRepository<ApplicationRefereeNomination, UUID> {
    List<ApplicationRefereeNomination> findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByCreatedAtAsc(UUID applicationId);
    Optional<ApplicationRefereeNomination> findByApplicationIdAndRefereeIdAndCurrentTrueAndDeletedAtIsNull(
            UUID applicationId, UUID refereeId);
}
