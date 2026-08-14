package zw.ac.uz.emhare.studentrecords.reporting;

import java.time.Instant;
import java.util.UUID;

/** Registration outcome keyed by the immutable Admissions source identifiers. @author Tinashe K */
public record AdmissionsRegistrationOutcome(
        UUID sourceApplicationId,
        UUID sourceOfferId,
        UUID studentId,
        String studentNumber,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        UUID intakeId,
        String registrationStatus,
        Instant registrationConfirmedAt) {}
