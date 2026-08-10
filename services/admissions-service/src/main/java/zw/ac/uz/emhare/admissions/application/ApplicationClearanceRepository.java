package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationClearanceRepository extends JpaRepository<ApplicationClearance, UUID> {
    Optional<ApplicationClearance> findByApplicationIdAndOutcomeAndDeletedAtIsNull(
            UUID applicationId, ApplicationClearanceOutcome outcome);
}
