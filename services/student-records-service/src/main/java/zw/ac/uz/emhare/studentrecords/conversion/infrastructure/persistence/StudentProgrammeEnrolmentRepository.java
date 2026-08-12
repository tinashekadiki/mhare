package zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProgrammeEnrolment;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentProgrammeEnrolmentRepository extends JpaRepository<StudentProgrammeEnrolment, UUID> {
    Optional<StudentProgrammeEnrolment> findByIdAndDeletedAtIsNull(UUID id);
    List<StudentProgrammeEnrolment> findAllByStudentIdAndDeletedAtIsNullOrderByCommencementDateDesc(UUID studentId);
}
