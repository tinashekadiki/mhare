package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPriorUzDeclaration;

/** @author Tinashe K */
public interface ApplicationPriorUzDeclarationRepository extends JpaRepository<ApplicationPriorUzDeclaration, UUID> {
    Optional<ApplicationPriorUzDeclaration> findByApplicationIdAndDeletedAtIsNull(UUID applicationId);
}
