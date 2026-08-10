package zw.ac.uz.emhare.studentrecords.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;

/** @author Tinashe K */
class RegistrationSessionTest {

    private static final Instant INITIATED_AT = Instant.parse("2027-07-01T08:00:00Z");
    private static final UUID ACTOR = UUID.fromString("98f755af-cbd9-4dbf-9ac8-df1abc6c4e38");

    @Test
    void requiresAcademicApprovalBeforeRegistryConfirmation() {
        RegistrationSession registration = registration();

        assertThrows(IllegalStateException.class, () -> registration.confirm(
                ACTOR, "Registry checked the record.", INITIATED_AT.plusSeconds(60), 0));
    }

    @Test
    void recordsTheGovernedSubmissionApprovalAndConfirmationLifecycle() {
        RegistrationSession registration = registration();

        assertEquals(RegistrationStatus.DRAFT, registration.getStatus());
        registration.submit("Student and curriculum reviewed.", INITIATED_AT.plusSeconds(60), 0);
        assertEquals(RegistrationStatus.SUBMITTED, registration.getStatus());
        registration.approveAcademically(
                ACTOR, "Academic unit approved the Module load.", INITIATED_AT.plusSeconds(120), 0);
        assertEquals(RegistrationStatus.ACADEMIC_APPROVED, registration.getStatus());
        registration.confirm(
                ACTOR, "Registry confirmed the registration.", INITIATED_AT.plusSeconds(180), 0);

        assertEquals(RegistrationStatus.CONFIRMED, registration.getStatus());
        assertNotNull(registration.getSubmittedAt());
        assertNotNull(registration.getAcademicApprovedAt());
        assertNotNull(registration.getConfirmedAt());
    }

    @Test
    void rejectsBlankDecisionReasons() {
        RegistrationSession registration = registration();

        assertThrows(IllegalArgumentException.class, () -> registration.submit(
                "  ", INITIATED_AT.plusSeconds(60), 0));
    }

    private RegistrationSession registration() {
        AcceptedOfferReadyForConversionEvent event = new AcceptedOfferReadyForConversionEvent(
                UUID.randomUUID(), AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION, INITIATED_AT,
                UUID.randomUUID(), "APP-2027-00001", UUID.randomUUID(), "OFR-2027-00001",
                UUID.randomUUID(), UUID.randomUUID(), "APL-2027-00001", "LOCAL", "Tariro", "Moyo",
                "tariro.moyo@example.test", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BACC", "Bachelor of Accountancy", UUID.randomUUID(), LocalDate.of(2027, 8, 16));
        StudentProfile student = new StudentProfile("STU-2027-0000001", event);
        StudentProgrammeEnrolment enrolment = new StudentProgrammeEnrolment(student, event);
        RegistrationCatalogue catalogue = new RegistrationCatalogue(
                UUID.randomUUID(), "2027-S1", "Semester 1", LocalDate.of(2027, 8, 16),
                LocalDate.of(2027, 12, 15), event.programmeVersionId(), event.programmeId(),
                event.programmeCode(), event.programmeName(), "2027",
                UUID.randomUUID(), "BUS", "Business School", UUID.randomUUID(), "UG", "Undergraduate", 1,
                List.of(new RegistrationModuleOption(
                        UUID.randomUUID(), UUID.randomUUID(), "ACC101", "Financial Accounting I",
                        "COMPULSORY", new BigDecimal("12.00"), new BigDecimal("50.00"), 1)));
        return new RegistrationSession(
                "REG-00000001", student, enrolment, catalogue, RegistrationType.NORMAL, INITIATED_AT);
    }
}
