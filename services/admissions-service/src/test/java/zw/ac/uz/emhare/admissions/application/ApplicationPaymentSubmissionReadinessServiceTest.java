package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationPaymentReferenceRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicationPaymentSubmissionReadinessServiceTest {

    @Mock private ApplicationPaymentReferenceRepository paymentReferenceRepository;
    @Mock private DocumentsReportingClient documentsReportingClient;

    @InjectMocks private ApplicationPaymentSubmissionReadinessService service;

    @Test
    void allowsSubmissionWhenProofOfPaymentHasBeenUploadedButNotYetVerified() {
        UUID applicationId = UUID.randomUUID();
        UUID financePaymentReferenceId = UUID.randomUUID();
        Application application = pendingPaymentApplication(applicationId);
        ApplicationPaymentReference paymentReference = mock(ApplicationPaymentReference.class);
        when(paymentReference.getFinancePaymentReferenceId()).thenReturn(financePaymentReferenceId);
        when(paymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(paymentReference));
        when(documentsReportingClient.getUploadedDocuments("FINANCE_RECORD", financePaymentReferenceId))
                .thenReturn(List.of(paymentProof("PENDING")));

        var readiness = service.evaluate(application);

        assertTrue(readiness.readyForSubmission());
        assertFalse(readiness.clearedForReview());
    }

    @Test
    void rejectsSubmissionWhenTheOnlyPaymentProofWasRejected() {
        UUID applicationId = UUID.randomUUID();
        UUID financePaymentReferenceId = UUID.randomUUID();
        Application application = pendingPaymentApplication(applicationId);
        ApplicationPaymentReference paymentReference = mock(ApplicationPaymentReference.class);
        when(paymentReference.getFinancePaymentReferenceId()).thenReturn(financePaymentReferenceId);
        when(paymentReferenceRepository.findByApplicationIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(paymentReference));
        when(documentsReportingClient.getUploadedDocuments("FINANCE_RECORD", financePaymentReferenceId))
                .thenReturn(List.of(paymentProof("REJECTED")));

        var readiness = service.evaluate(application);

        assertFalse(readiness.readyForSubmission());
        assertFalse(readiness.clearedForReview());
    }

    private Application pendingPaymentApplication(UUID applicationId) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.isPaymentRequired()).thenReturn(true);
        when(application.canEnterReview()).thenReturn(false);
        return application;
    }

    private UploadedDocumentSnapshot paymentProof(String verificationStatus) {
        return new UploadedDocumentSnapshot(
                UUID.randomUUID(), "FINANCE_RECORD", UUID.randomUUID(), "PROOF_OF_PAYMENT",
                "payment.pdf", "application/pdf", 1200L, "checksum", UUID.randomUUID(),
                Instant.parse("2026-08-10T07:00:00Z"), verificationStatus, null, null, null,
                verificationStatus.equals("REJECTED") ? "Unreadable receipt" : null, null, 0L);
    }
}
