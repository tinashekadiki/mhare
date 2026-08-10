package zw.ac.uz.emhare.assessmentresults.roster;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationRosterImportRepository extends JpaRepository<RegistrationRosterImport, UUID> {
    Optional<RegistrationRosterImport> findByRegistrationSessionIdAndDeletedAtIsNull(UUID registrationSessionId);
}
