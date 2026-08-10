package zw.ac.uz.emhare.studentrecords.conversion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;

/** @author Tinashe K */
@Service
public class StudentConversionService {
    private final StudentProfileRepository studentRepository;
    private final StudentProgrammeEnrolmentRepository enrolmentRepository;
    private final StudentConversionRequestRepository conversionRepository;
    private final StudentStatusEventRepository statusEventRepository;
    private final StudentRecordsIntegrationOutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public StudentConversionService(
            StudentProfileRepository studentRepository,
            StudentProgrammeEnrolmentRepository enrolmentRepository,
            StudentConversionRequestRepository conversionRepository,
            StudentStatusEventRepository statusEventRepository,
            StudentRecordsIntegrationOutboxService outboxService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.conversionRepository = conversionRepository;
        this.statusEventRepository = statusEventRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public StudentConversionSummary startConversion(AcceptedOfferReadyForConversionEvent event) {
        StudentConversionRequest existing = conversionRepository
                .findBySourceOfferIdAndDeletedAtIsNull(event.offerId()).orElse(null);
        if (existing != null) return StudentConversionSummary.from(existing);
        validate(event);
        Instant now = clock.instant();
        StudentProfile student = studentRepository.saveAndFlush(
                new StudentProfile(nextStudentNumber(event.commencementDate().getYear()), event));
        StudentProgrammeEnrolment enrolment = enrolmentRepository.saveAndFlush(
                new StudentProgrammeEnrolment(student, event));
        StudentConversionRequest conversion = conversionRepository.saveAndFlush(new StudentConversionRequest(
                event.eventId(), event.applicationId(), event.offerId(), student, enrolment, now));
        statusEventRepository.save(new StudentStatusEvent(
                student, null, StudentStatus.PROVISIONING,
                "Accepted offer received; finance and portal provisioning requested.", now));
        outboxService.enqueueProvisioningRequests(conversion);
        return StudentConversionSummary.from(conversion);
    }

    @Transactional
    public void recordFinanceProvisioning(UUID conversionRequestId, boolean successful, String failureReason) {
        StudentConversionRequest conversion = conversion(conversionRequestId);
        if (conversion.recordFinanceProvisioning(successful, failureReason)) completeWhenReady(conversion);
    }

    @Transactional
    public void recordPortalProvisioning(UUID conversionRequestId, boolean successful, String failureReason) {
        StudentConversionRequest conversion = conversion(conversionRequestId);
        if (conversion.recordPortalProvisioning(successful, failureReason)) completeWhenReady(conversion);
    }

    @Transactional(readOnly = true)
    public List<StudentConversionSummary> listConversions() {
        return conversionRepository.findAllByDeletedAtIsNullOrderByRequestedAtDesc().stream()
                .map(StudentConversionSummary::from).toList();
    }

    @Transactional
    public StudentConversionSummary retryProvisioning(
            UUID conversionRequestId, String reason, UUID actorUserId) {
        StudentConversionRequest conversion = conversion(conversionRequestId);
        conversion.retryProvisioning(reason, actorUserId, clock.instant());
        outboxService.enqueueProvisioningRequests(conversion);
        return StudentConversionSummary.from(conversion);
    }

    private void completeWhenReady(StudentConversionRequest conversion) {
        if (!conversion.canComplete()) return;
        Instant now = clock.instant();
        conversion.complete(now);
        statusEventRepository.save(new StudentStatusEvent(
                conversion.getStudent(), StudentStatus.PROVISIONING, StudentStatus.ACTIVE,
                "Finance account and student portal access provisioned.", now));
        outboxService.enqueueConversionCompleted(conversion);
    }

    private StudentConversionRequest conversion(UUID id) {
        return conversionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student conversion request not found."));
    }

    private String nextStudentNumber(int cohortYear) {
        Long sequence = jdbcTemplate.queryForObject("SELECT nextval('student_number_sequence')", Long.class);
        if (sequence == null) throw new IllegalStateException("Student number sequence did not return a value.");
        return "STU-%d-%07d".formatted(cohortYear, sequence);
    }

    private void validate(AcceptedOfferReadyForConversionEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION
                || event.applicationId() == null || event.offerId() == null || event.applicantId() == null
                || event.applicantUserId() == null || event.programmeChoiceId() == null
                || event.programmeId() == null || event.programmeVersionId() == null
                || event.intakeId() == null || event.commencementDate() == null) {
            throw new IllegalArgumentException("Accepted-offer conversion event contract is invalid or unsupported.");
        }
    }
}
