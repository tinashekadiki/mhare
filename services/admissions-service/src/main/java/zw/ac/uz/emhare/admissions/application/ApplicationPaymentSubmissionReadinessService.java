package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** Determines whether an applicant has supplied enough payment evidence to submit. @author Tinashe K */
@Service
public class ApplicationPaymentSubmissionReadinessService {

    private static final String PAYMENT_PROOF_DOCUMENT_TYPE = "PROOF_OF_PAYMENT";

    private final ApplicationPaymentReferenceRepository paymentReferenceRepository;
    private final DocumentsReportingClient documentsReportingClient;

    public ApplicationPaymentSubmissionReadinessService(
            ApplicationPaymentReferenceRepository paymentReferenceRepository,
            DocumentsReportingClient documentsReportingClient) {
        this.paymentReferenceRepository = paymentReferenceRepository;
        this.documentsReportingClient = documentsReportingClient;
    }

    public PaymentSubmissionReadiness evaluate(Application application) {
        if (!application.isPaymentRequired()) {
            return new PaymentSubmissionReadiness(true, false, "No application fee is required.");
        }
        if (application.canEnterReview()) {
            return new PaymentSubmissionReadiness(true, true, "Application fee cleared.");
        }
        UUID financePaymentReferenceId = paymentReferenceRepository
                .findByApplicationIdAndDeletedAtIsNull(application.getId())
                .map(ApplicationPaymentReference::getFinancePaymentReferenceId)
                .orElse(null);
        if (financePaymentReferenceId == null) {
            return missingEvidence();
        }
        try {
            boolean proofUploaded = documentsReportingClient
                    .getUploadedDocuments("FINANCE_RECORD", financePaymentReferenceId).stream()
                    .anyMatch(document -> PAYMENT_PROOF_DOCUMENT_TYPE.equals(document.documentTypeCode())
                            && !"REJECTED".equals(document.verificationStatus()));
            return proofUploaded
                    ? new PaymentSubmissionReadiness(
                            true, false, "Proof of payment uploaded. Finance verification is pending.")
                    : missingEvidence();
        } catch (ServiceDependencyUnavailableException exception) {
            return new PaymentSubmissionReadiness(
                    false, false, "Payment evidence could not be checked. Try again.");
        }
    }

    private PaymentSubmissionReadiness missingEvidence() {
        return new PaymentSubmissionReadiness(
                false, false, "Upload proof of payment or pay online before submitting.");
    }

    public record PaymentSubmissionReadiness(
            boolean readyForSubmission,
            boolean clearedForReview,
            String summary) {
    }
}
