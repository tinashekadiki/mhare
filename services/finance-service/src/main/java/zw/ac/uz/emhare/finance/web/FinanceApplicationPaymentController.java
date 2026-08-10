package zw.ac.uz.emhare.finance.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.finance.payment.ApplicationPaymentReferenceSummary;
import zw.ac.uz.emhare.finance.payment.ConfirmApplicationPaymentCommand;
import zw.ac.uz.emhare.finance.payment.CreateApplicationPaymentReferenceCommand;
import zw.ac.uz.emhare.finance.payment.FinanceApplicationPaymentService;
import zw.ac.uz.emhare.finance.payment.provider.ApplicationPaymentCheckoutViews.HostedCheckout;
import zw.ac.uz.emhare.finance.payment.provider.ApplicationPaymentCheckoutViews.PaymentOptions;
import zw.ac.uz.emhare.finance.payment.provider.CbzIveriLiteCheckoutService;
import zw.ac.uz.emhare.finance.payment.provider.CbzIveriLiteReturnService;
import zw.ac.uz.emhare.finance.payment.provider.StartHostedCheckoutRequest;

@RestController
@RequestMapping
public class FinanceApplicationPaymentController {

    private final FinanceApplicationPaymentService financeApplicationPaymentService;
    private final CbzIveriLiteCheckoutService cbzIveriLiteCheckoutService;
    private final CbzIveriLiteReturnService cbzIveriLiteReturnService;

    public FinanceApplicationPaymentController(
            FinanceApplicationPaymentService financeApplicationPaymentService,
            CbzIveriLiteCheckoutService cbzIveriLiteCheckoutService,
            CbzIveriLiteReturnService cbzIveriLiteReturnService) {
        this.financeApplicationPaymentService = financeApplicationPaymentService;
        this.cbzIveriLiteCheckoutService = cbzIveriLiteCheckoutService;
        this.cbzIveriLiteReturnService = cbzIveriLiteReturnService;
    }

    @PostMapping("/internal/finance/application-payment-references")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationPaymentReferenceSummary ensurePaymentReference(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateApplicationPaymentReferenceRequest request) {
        return financeApplicationPaymentService.ensurePaymentReference(new CreateApplicationPaymentReferenceCommand(
                request.applicationId(),
                request.applicantUserId(),
                authenticatedKeycloakUserId(authentication),
                request.amountDue(),
                request.currencyCode(),
                request.requiredForSubmission()));
    }

    @GetMapping("/api/finance/application-payment-references/by-application/{applicationId}")
    public ApplicationPaymentReferenceSummary findApplicantPaymentReference(
            JwtAuthenticationToken authentication,
            @PathVariable("applicationId") UUID applicationId) {
        return financeApplicationPaymentService.findApplicantPaymentReference(
                applicationId,
                authenticatedKeycloakUserId(authentication));
    }

    @GetMapping("/api/finance/application-payment-references/by-application/{applicationId}/payment-options")
    public PaymentOptions findApplicantPaymentOptions(
            JwtAuthenticationToken authentication,
            @PathVariable("applicationId") UUID applicationId) {
        financeApplicationPaymentService.findApplicantPaymentReference(
                applicationId,
                authenticatedKeycloakUserId(authentication));
        return cbzIveriLiteCheckoutService.paymentOptions();
    }

    @PostMapping("/api/finance/application-payment-references/by-application/{applicationId}/online-checkouts")
    @ResponseStatus(HttpStatus.CREATED)
    public HostedCheckout startOnlineCheckout(
            JwtAuthenticationToken authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody StartHostedCheckoutRequest request) {
        return cbzIveriLiteCheckoutService.startCheckout(
                applicationId,
                authenticatedKeycloakUserId(authentication),
                request.emailAddress());
    }

    @PostMapping("/api/finance/application-payment-references/by-application/{applicationId}/online-checkouts/reconcile")
    public ApplicationPaymentReferenceSummary reconcileOnlineCheckout(
            JwtAuthenticationToken authentication,
            @PathVariable("applicationId") UUID applicationId,
            @RequestBody(required = false) ReconcileOnlineCheckoutRequest request) {
        return cbzIveriLiteReturnService.reconcileApplicantCheckout(
                applicationId,
                authenticatedKeycloakUserId(authentication),
                request == null ? null : request.attemptId());
    }

    @PostMapping("/internal/finance/application-payment-references/query")
    @PreAuthorize("hasAnyRole('admissions-officer', 'finance-officer', 'system-admin')")
    public List<ApplicationPaymentReferenceSummary> findPaymentReferencesForOperations(
            @Valid @RequestBody ApplicationPaymentReferenceQueryRequest request) {
        return financeApplicationPaymentService.findPaymentReferences(request.applicationIds().stream().toList());
    }

    @PostMapping("/api/finance/application-payment-references/reconciled-payments")
    @PreAuthorize("hasAnyRole('finance-officer', 'system-admin')")
    public ApplicationPaymentReferenceSummary confirmReconciledPayment(
            @Valid @RequestBody ConfirmReconciledApplicationPaymentRequest request) {
        return financeApplicationPaymentService.confirmReconciledPayment(new ConfirmApplicationPaymentCommand(
                request.applicationId(),
                request.providerCode(),
                request.providerTransactionReference(),
                request.amount(),
                request.currencyCode(),
                request.paidAt(),
                request.providerEventFingerprint()));
    }

    private UUID authenticatedKeycloakUserId(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Authenticated Keycloak subject must be a UUID.", exception);
        }
    }

    public record ReconcileOnlineCheckoutRequest(UUID attemptId) {
    }
}
