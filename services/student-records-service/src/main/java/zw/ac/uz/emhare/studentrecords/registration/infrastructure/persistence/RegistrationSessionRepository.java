package zw.ac.uz.emhare.studentrecords.registration.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationSession;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationStatus;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, UUID> {
    Optional<RegistrationSession> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByStudentIdAndAcademicPeriodIdAndStatusNotAndDeletedAtIsNull(
            UUID studentId, UUID academicPeriodId, RegistrationStatus status);
    boolean existsByRegistrationNumberAndProgrammeVersionId(String registrationNumber, UUID programmeVersionId);
    List<RegistrationSession> findAllByDeletedAtIsNullOrderByInitiatedAtDesc();
    List<RegistrationSession> findAllByStudentIdAndDeletedAtIsNullOrderByInitiatedAtDesc(UUID studentId);
}
