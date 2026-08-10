package zw.ac.uz.emhare.studentrecords.conversion;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentProgrammeEnrolmentRepository extends JpaRepository<StudentProgrammeEnrolment, UUID> {
    Optional<StudentProgrammeEnrolment> findByIdAndDeletedAtIsNull(UUID id);
    List<StudentProgrammeEnrolment> findAllByStudentIdAndDeletedAtIsNullOrderByCommencementDateDesc(UUID studentId);
}
