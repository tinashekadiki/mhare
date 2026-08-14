package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/** Immutable before-and-after intake evidence from Envers. @author Tinashe K */
public record AdmissionsIntakeMovementRow(
        UUID applicationId,
        String applicationNumber,
        String applicantNumber,
        String applicantName,
        String applicationStatus,
        UUID previousIntakeId,
        String previousIntakeCode,
        String previousIntakeName,
        UUID newIntakeId,
        String newIntakeCode,
        String newIntakeName,
        UUID changedByUserId,
        Instant changedAt,
        String reason) {}
