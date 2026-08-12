package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.OfferCondition;
import zw.ac.uz.emhare.admissions.domain.model.OfferResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record AdmissionOfferSummary(
        UUID id,
        UUID offerBatchId,
        String offerNumber,
        UUID applicationId,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        UUID programmeChoiceId,
        UUID programmeId,
        UUID programmeVersionId,
        String programmeCode,
        String programmeName,
        UUID intakeId,
        String offerType,
        String status,
        UUID currentDocumentVersionId,
        UUID currentPublicationId,
        boolean amendmentPending,
        String conditionsText,
        Instant acceptanceDeadline,
        LocalDate registrationDate,
        LocalDate orientationDate,
        LocalDate commencementDate,
        UUID generatedDocumentId,
        Instant approvedAt,
        Instant sentAt,
        Instant expiredAt,
        String expiryReason,
        Instant conversionRequestedAt,
        UUID conversionRequestId,
        UUID convertedStudentId,
        String convertedStudentNumber,
        Instant convertedAt,
        List<OfferConditionSummary> conditions,
        OfferResponseSummary response) {

    static AdmissionOfferSummary from(
            AdmissionOffer offer,
            List<OfferCondition> conditions,
            OfferResponse response) {
        return new AdmissionOfferSummary(
                offer.getId(), offer.getOfferBatch() == null ? null : offer.getOfferBatch().getId(),
                offer.getOfferNumber(), offer.getApplication().getId(),
                offer.getApplication().getApplicationNumber(), offer.getApplication().getApplicant().getApplicantNumber(),
                offer.getApplication().getApplicant().getDisplayName(),
                offer.getProgrammeChoice().getId(), offer.getProgrammeId(), offer.getProgrammeVersionId(),
                offer.getProgrammeCode(), offer.getProgrammeName(), offer.getIntakeId(),
                offer.getOfferType() == null ? null : offer.getOfferType().name(),
                offer.getStatus().name(),
                offer.getCurrentDocumentVersion() == null ? null : offer.getCurrentDocumentVersion().getId(),
                offer.getCurrentPublication() == null ? null : offer.getCurrentPublication().getId(),
                offer.isAmendmentPending(), offer.getConditionsText(),
                offer.getAcceptanceDeadline(), offer.getRegistrationDate(), offer.getOrientationDate(),
                offer.getCommencementDate(), offer.getGeneratedDocumentId(), offer.getApprovedAt(), offer.getSentAt(),
                offer.getExpiredAt(), offer.getExpiryReason(), offer.getConversionRequestedAt(),
                offer.getConversionRequestId(), offer.getConvertedStudentId(),
                offer.getConvertedStudentNumber(), offer.getConvertedAt(),
                conditions.stream().map(OfferConditionSummary::from).toList(),
                response == null ? null : OfferResponseSummary.from(response));
    }

    public record OfferConditionSummary(
            UUID id,
            String code,
            String description,
            boolean required,
            String status,
            UUID resolvedByUserId,
            Instant resolvedAt,
            String resolutionNotes) {
        static OfferConditionSummary from(OfferCondition condition) {
            return new OfferConditionSummary(
                    condition.getId(), condition.getConditionCode(), condition.getDescription(), condition.isRequired(),
                    condition.getStatus().name(), condition.getSatisfiedByUserId(), condition.getSatisfiedAt(),
                    condition.getResolutionNotes());
        }
    }

    public record OfferResponseSummary(String response, Instant respondedAt, String notes) {
        static OfferResponseSummary from(OfferResponse response) {
            return new OfferResponseSummary(response.getResponse().name(), response.getRespondedAt(), response.getNotes());
        }
    }
}
