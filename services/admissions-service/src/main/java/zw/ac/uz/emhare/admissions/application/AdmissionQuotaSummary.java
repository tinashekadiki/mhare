package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;

/** @author Tinashe K */
public record AdmissionQuotaSummary(
        UUID id,
        UUID intakeId,
        UUID programmeId,
        String programmeCode,
        String programmeName,
        String quotaTypeCode,
        int capacity,
        int reservedCapacity,
        long version) { }
