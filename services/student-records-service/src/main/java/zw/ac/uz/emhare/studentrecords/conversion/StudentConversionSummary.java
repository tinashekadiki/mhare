package zw.ac.uz.emhare.studentrecords.conversion;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentConversionRequest;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProgrammeEnrolment;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record StudentConversionSummary(
        UUID id,
        String status,
        String financeProvisioningStatus,
        String portalProvisioningStatus,
        UUID sourceApplicationId,
        UUID sourceOfferId,
        UUID studentId,
        String studentNumber,
        String studentStatus,
        UUID programmeEnrolmentId,
        UUID programmeId,
        UUID programmeVersionId,
        String programmeCode,
        String programmeName,
        String programmeEnrolmentStatus,
        Instant requestedAt,
        Instant completedAt,
        String failureReason,
        int retryCount,
        Instant lastRetryAt,
        UUID lastRetryByUserId,
        String lastRetryReason) {

    static StudentConversionSummary from(StudentConversionRequest request) {
        StudentProfile student = request.getStudent();
        StudentProgrammeEnrolment enrolment = request.getProgrammeEnrolment();
        return new StudentConversionSummary(
                request.getId(), request.getStatus().name(), request.getFinanceProvisioningStatus().name(),
                request.getPortalProvisioningStatus().name(), request.getSourceApplicationId(), request.getSourceOfferId(),
                student.getId(), student.getStudentNumber(), student.getStatus().name(), enrolment.getId(),
                enrolment.getProgrammeId(), enrolment.getProgrammeVersionId(),
                enrolment.getProgrammeCode(), enrolment.getProgrammeName(), enrolment.getStatus().name(),
                request.getRequestedAt(), request.getCompletedAt(), request.getFailureReason(),
                request.getRetryCount(), request.getLastRetryAt(), request.getLastRetryByUserId(),
                request.getLastRetryReason());
    }
}
