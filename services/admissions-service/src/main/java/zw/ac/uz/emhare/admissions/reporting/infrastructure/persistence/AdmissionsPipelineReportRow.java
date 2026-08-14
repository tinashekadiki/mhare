package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/** Flat reporting projection used to aggregate application and choice dimensions safely. @author Tinashe K */
public record AdmissionsPipelineReportRow(
        UUID applicationId,
        UUID applicantId,
        String applicationStatus,
        boolean paymentRequired,
        Instant paymentConfirmedAt,
        UUID paymentOverrideByUserId,
        UUID intakeId,
        String intakeCode,
        String intakeName,
        UUID applicationTypeId,
        String applicationTypeCode,
        String applicationTypeName,
        String applicantCategoryCode,
        String genderCode,
        UUID choiceId,
        Integer choiceRank,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        String owningAcademicUnitName) {}
