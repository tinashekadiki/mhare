package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;

/** @author Tinashe K */
class StudentConversionDomainTest {

    private static final Instant REQUESTED_AT = Instant.parse("2027-01-08T10:15:30Z");
    private static final Instant COMPLETED_AT = Instant.parse("2027-01-08T10:20:30Z");

    @Test
    void activatesStudentAndEnrolmentOnlyAfterBothProvisioningOperationsComplete() {
        StudentConversionRequest conversion = conversionRequest();

        assertTrue(conversion.recordFinanceProvisioning(true, null));
        assertFalse(conversion.canComplete());
        assertEquals(StudentStatus.PROVISIONING, conversion.getStudent().getStatus());
        assertEquals(
                ProgrammeEnrolmentStatus.PROVISIONING,
                conversion.getProgrammeEnrolment().getStatus());

        assertTrue(conversion.recordPortalProvisioning(true, null));
        assertTrue(conversion.canComplete());
        conversion.complete(COMPLETED_AT);

        assertEquals(StudentConversionStatus.COMPLETED, conversion.getStatus());
        assertEquals(StudentStatus.ACTIVE, conversion.getStudent().getStatus());
        assertEquals(
                ProgrammeEnrolmentStatus.ACTIVE,
                conversion.getProgrammeEnrolment().getStatus());
        assertEquals(COMPLETED_AT, conversion.getCompletedAt());
        assertNull(conversion.getFailureReason());
    }

    @Test
    void repeatedProvisioningAcknowledgementDoesNotAdvanceStateTwice() {
        StudentConversionRequest conversion = conversionRequest();

        assertTrue(conversion.recordFinanceProvisioning(true, null));
        assertFalse(conversion.recordFinanceProvisioning(true, null));

        assertEquals(ProvisioningStatus.COMPLETED, conversion.getFinanceProvisioningStatus());
        assertEquals(ProvisioningStatus.PENDING, conversion.getPortalProvisioningStatus());
        assertEquals(StudentConversionStatus.PROVISIONING, conversion.getStatus());
    }

    @Test
    void failedProvisioningPreventsActivationAndRetainsOperationalReason() {
        StudentConversionRequest conversion = conversionRequest();

        assertTrue(conversion.recordPortalProvisioning(false, "Keycloak user is missing"));

        assertEquals(StudentConversionStatus.FAILED, conversion.getStatus());
        assertEquals(ProvisioningStatus.FAILED, conversion.getPortalProvisioningStatus());
        assertEquals(StudentStatus.PROVISIONING, conversion.getStudent().getStatus());
        assertTrue(conversion.getFailureReason().contains("Keycloak user is missing"));
        assertThrows(IllegalStateException.class, () -> conversion.complete(COMPLETED_AT));
    }

    @Test
    void governedRetryResetsOnlyFailedProvisioningAndRetainsAuditEvidence() {
        StudentConversionRequest conversion = conversionRequest();
        UUID actorUserId = UUID.randomUUID();
        conversion.recordFinanceProvisioning(true, null);
        conversion.recordPortalProvisioning(false, "Keycloak user is missing");

        conversion.retryProvisioning(
                "Core Identity user was synchronised from Keycloak.", actorUserId, COMPLETED_AT);

        assertEquals(StudentConversionStatus.PROVISIONING, conversion.getStatus());
        assertEquals(ProvisioningStatus.COMPLETED, conversion.getFinanceProvisioningStatus());
        assertEquals(ProvisioningStatus.PENDING, conversion.getPortalProvisioningStatus());
        assertFalse(conversion.needsFinanceProvisioning());
        assertTrue(conversion.needsPortalProvisioning());
        assertNull(conversion.getFailureReason());
        assertEquals(1, conversion.getRetryCount());
        assertEquals(COMPLETED_AT, conversion.getLastRetryAt());
        assertEquals(actorUserId, conversion.getLastRetryByUserId());
        assertEquals(
                "Core Identity user was synchronised from Keycloak.",
                conversion.getLastRetryReason());
    }

    private StudentConversionRequest conversionRequest() {
        AcceptedOfferReadyForConversionEvent event = acceptedOfferEvent();
        StudentProfile student = new StudentProfile("STU-2027-0000001", event);
        StudentProgrammeEnrolment enrolment = new StudentProgrammeEnrolment(student, event);
        return new StudentConversionRequest(
                event.eventId(),
                event.applicationId(),
                event.offerId(),
                student,
                enrolment,
                REQUESTED_AT);
    }

    private AcceptedOfferReadyForConversionEvent acceptedOfferEvent() {
        return new AcceptedOfferReadyForConversionEvent(
                UUID.randomUUID(),
                AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION,
                REQUESTED_AT,
                UUID.randomUUID(),
                "APP-2027-00001",
                UUID.randomUUID(),
                "OFR-2027-00001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "APL-2027-00001",
                "LOCAL",
                "Tariro",
                "Moyo",
                "tariro.moyo@example.test",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BACC",
                "Bachelor of Accountancy",
                UUID.randomUUID(),
                LocalDate.of(2027, 8, 16));
    }
}
