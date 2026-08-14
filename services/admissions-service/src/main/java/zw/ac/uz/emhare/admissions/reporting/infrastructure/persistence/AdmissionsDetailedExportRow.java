package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One application-level row for an operational admissions export. @author Tinashe K */
public record AdmissionsDetailedExportRow(
        UUID applicationId,
        String applicationNumber,
        Instant submittedAt,
        String applicationStatus,
        String paymentStatus,
        String intakeCode,
        String intakeName,
        String applicationTypeCode,
        String applicationTypeName,
        String applicantNumber,
        String applicantName,
        String primaryEmail,
        String primaryPhone,
        String applicantCategoryCode,
        String genderCode,
        BigDecimal calculatedTotalPoints,
        String programmeChoices) {}
