package zw.ac.uz.emhare.studentrecords.conversion;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProfileRepository;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProgrammeEnrolmentRepository;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @author Tinashe K */
@Service
public class StudentSelfServiceService {

    private final StudentProfileRepository studentRepository;
    private final StudentProgrammeEnrolmentRepository programmeEnrolmentRepository;

    public StudentSelfServiceService(
            StudentProfileRepository studentRepository,
            StudentProgrammeEnrolmentRepository programmeEnrolmentRepository) {
        this.studentRepository = studentRepository;
        this.programmeEnrolmentRepository = programmeEnrolmentRepository;
    }

    @Transactional(readOnly = true)
    public StudentWorkspaceSummary workspaceForUser(UUID userId) {
        StudentProfile student = studentRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No student record is linked to the authenticated user."));
        return StudentWorkspaceSummary.from(
                student,
                programmeEnrolmentRepository
                        .findAllByStudentIdAndDeletedAtIsNullOrderByCommencementDateDesc(student.getId()));
    }
}
