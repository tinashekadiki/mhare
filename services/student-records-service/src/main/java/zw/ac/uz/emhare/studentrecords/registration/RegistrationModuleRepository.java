package zw.ac.uz.emhare.studentrecords.registration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationModuleRepository extends JpaRepository<RegistrationModule, UUID> {
    List<RegistrationModule> findAllByRegistrationSessionIdOrderBySortOrderAsc(UUID registrationSessionId);
    long countByCurriculumModuleId(UUID curriculumModuleId);
}
