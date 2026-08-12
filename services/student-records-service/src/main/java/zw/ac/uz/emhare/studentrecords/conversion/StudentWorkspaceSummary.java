package zw.ac.uz.emhare.studentrecords.conversion;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProgrammeEnrolment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.ProgrammeEnrolmentStatus;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentStatus;

/** @author Tinashe K */
public record StudentWorkspaceSummary(
        UUID id,
        String studentNumber,
        String firstName,
        String middleNames,
        String lastName,
        String primaryEmail,
        String primaryPhone,
        LocalDate dateOfBirth,
        String genderCode,
        String disabilityStatusCode,
        StudentStatus status,
        Instant activatedAt,
        List<ProgrammeEnrolmentSummary> programmeEnrolments) {

    public record ProgrammeEnrolmentSummary(
            UUID id,
            UUID programmeId,
            UUID programmeVersionId,
            String programmeCode,
            String programmeName,
            UUID intakeId,
            LocalDate commencementDate,
            ProgrammeEnrolmentStatus status,
            String statusReason,
            Instant approvedAt) {

        static ProgrammeEnrolmentSummary from(StudentProgrammeEnrolment enrolment) {
            return new ProgrammeEnrolmentSummary(
                    enrolment.getId(),
                    enrolment.getProgrammeId(),
                    enrolment.getProgrammeVersionId(),
                    enrolment.getProgrammeCode(),
                    enrolment.getProgrammeName(),
                    enrolment.getIntakeId(),
                    enrolment.getCommencementDate(),
                    enrolment.getStatus(),
                    enrolment.getStatusReason(),
                    enrolment.getApprovedAt());
        }
    }

    static StudentWorkspaceSummary from(
            StudentProfile student,
            List<StudentProgrammeEnrolment> programmeEnrolments) {
        return new StudentWorkspaceSummary(
                student.getId(),
                student.getStudentNumber(),
                student.getFirstName(),
                student.getMiddleNames(),
                student.getLastName(),
                student.getPrimaryEmail(),
                student.getPrimaryPhone(),
                student.getDateOfBirth(),
                student.getGenderCode(),
                student.getDisabilityStatusCode(),
                student.getStatus(),
                student.getActivatedAt(),
                programmeEnrolments.stream().map(ProgrammeEnrolmentSummary::from).toList());
    }
}
