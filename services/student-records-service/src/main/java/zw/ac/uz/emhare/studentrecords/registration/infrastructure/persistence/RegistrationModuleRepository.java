package zw.ac.uz.emhare.studentrecords.registration.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationModule;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationModuleRepository extends JpaRepository<RegistrationModule, UUID> {
    List<RegistrationModule> findAllByRegistrationSessionIdOrderBySortOrderAsc(UUID registrationSessionId);
    long countByCurriculumModuleId(UUID curriculumModuleId);
}
