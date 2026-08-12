package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearance;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;

import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

public record ApplicationSummary(
        UUID id,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        UUID intakeId,
        String intakeCode,
        UUID applicationTypeId,
        String applicationTypeName,
        String status,
        boolean paymentRequired,
        String paymentClearanceStatus,
        String paymentWaiverReason,
        boolean canSubmit,
        boolean canEnterReview,
        BigDecimal calculatedTotalPoints,
        Instant pointsCalculatedAt,
        String admissionsClearanceStatus,
        UUID confirmedByUserId,
        Instant confirmedAt,
        String confirmationReason,
        ApplicationPaymentSummary payment,
        List<ApplicationProgrammeChoiceSummary> programmeChoices) {

    static ApplicationSummary from(
            Application application,
            ApplicationPaymentReference paymentReference,
            List<ApplicationProgrammeChoice> programmeChoices) {
        return from(application, paymentReference, programmeChoices, null);
    }

    static ApplicationSummary from(
            Application application,
            ApplicationPaymentReference paymentReference,
            List<ApplicationProgrammeChoice> programmeChoices,
            ApplicationClearance clearance) {
        return new ApplicationSummary(
                application.getId(),
                application.getApplicationNumber(),
                application.getApplicant().getApplicantNumber(),
                application.getApplicant().getDisplayName(),
                application.getAdmissionCycle().getIntakeId(),
                application.getAdmissionCycle().getCode(),
                application.getApplicationType().getId(),
                application.getApplicationType().getName(),
                application.getStatus().name(),
                application.isPaymentRequired(),
                paymentClearanceStatus(application, paymentReference),
                application.getPaymentOverrideReason(),
                application.canSubmit(),
                application.canEnterReview(),
                application.getCalculatedTotalPoints(),
                application.getPointsCalculatedAt(),
                clearance == null ? "NOT_CONFIRMED" : clearance.getOutcome().name(),
                clearance == null ? null : clearance.getConfirmedByUserId(),
                clearance == null ? null : clearance.getConfirmedAt(),
                clearance == null ? null : clearance.getReason(),
                paymentReference == null ? null : ApplicationPaymentSummary.from(paymentReference),
                programmeChoices.stream().map(ApplicationProgrammeChoiceSummary::from).toList());
    }

    private static String paymentClearanceStatus(
            Application application,
            ApplicationPaymentReference paymentReference) {
        if (!application.isPaymentRequired()) {
            return "NOT_REQUIRED";
        }
        if (application.getPaymentOverrideByUserId() != null) {
            return "WAIVED";
        }
        if (application.getPaymentConfirmedAt() != null) {
            return "PAID";
        }
        if (paymentReference != null && "UNRATED".equals(paymentReference.getRatingStatus())) {
            return "UNRATED";
        }
        return "PENDING";
    }
}
