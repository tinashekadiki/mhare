package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentConversionRequest;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentConversionRequestRepository;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProfileRepository;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProgrammeEnrolmentRepository;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentStatusEventRepository;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;

/** @author Tinashe K */
class StudentConversionServiceTest {

    @Test
    void usesTheConfiguredStudentNumberGeneratorWhenConvertingAnAcceptedOffer() {
        StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
        StudentProgrammeEnrolmentRepository enrolmentRepository = mock(StudentProgrammeEnrolmentRepository.class);
        StudentConversionRequestRepository conversionRepository = mock(StudentConversionRequestRepository.class);
        StudentStatusEventRepository statusEventRepository = mock(StudentStatusEventRepository.class);
        StudentRecordsIntegrationOutboxService outboxService = mock(StudentRecordsIntegrationOutboxService.class);
        StudentNumberGenerator numberGenerator = mock(StudentNumberGenerator.class);
        Clock clock = Clock.fixed(Instant.parse("2027-01-08T10:15:30Z"), ZoneOffset.UTC);
        AcceptedOfferReadyForConversionEvent event = acceptedOfferEvent(clock.instant());

        when(conversionRepository.findBySourceOfferIdAndDeletedAtIsNull(event.offerId()))
                .thenReturn(Optional.empty());
        when(numberGenerator.nextStudentNumber("LOCAL", 2027)).thenReturn("R270042A");
        when(studentRepository.saveAndFlush(any(StudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(enrolmentRepository.saveAndFlush(any(StudentProgrammeEnrolment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversionRepository.saveAndFlush(any(StudentConversionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StudentConversionService service = new StudentConversionService(
                studentRepository, enrolmentRepository, conversionRepository, statusEventRepository,
                outboxService, numberGenerator, clock);

        StudentConversionSummary summary = service.startConversion(event);

        assertEquals("R270042A", summary.studentNumber());
        verify(numberGenerator).nextStudentNumber("LOCAL", 2027);
    }

    private AcceptedOfferReadyForConversionEvent acceptedOfferEvent(Instant occurredAt) {
        return new AcceptedOfferReadyForConversionEvent(
                UUID.randomUUID(),
                AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                UUID.randomUUID(),
                "A000042",
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
