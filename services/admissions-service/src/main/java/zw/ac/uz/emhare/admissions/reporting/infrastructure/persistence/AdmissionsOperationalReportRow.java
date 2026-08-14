package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Denormalised, read-only evidence row used by the governed report families. @author Tinashe K */
public record AdmissionsOperationalReportRow(
        UUID applicationId,
        UUID applicantId,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        Instant submittedAt,
        String applicationStatus,
        BigDecimal totalPoints,
        UUID intakeId,
        String intakeCode,
        String intakeName,
        UUID applicationTypeId,
        String applicationTypeCode,
        String applicationTypeName,
        String applicantCategoryCode,
        String genderCode,
        String disabilityStatusCode,
        String sponsorTypeCode,
        UUID choiceId,
        Integer choiceRank,
        String choiceStatus,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        String owningAcademicUnitName,
        String qualificationSummary,
        String schoolsAttended,
        String decision,
        Instant decidedAt,
        UUID offerId,
        String offerNumber,
        String offerStatus,
        String offerType,
        Instant publishedAt,
        String emailDeliveryStatus,
        String offerResponse) {}
