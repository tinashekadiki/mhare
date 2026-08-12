package zw.ac.uz.emhare.admissions.domain.model;

import java.util.UUID;

/** Immutable academic catalogue values captured with an application choice. @author Tinashe K */
public record ProgrammeSelectionSnapshot(
        UUID programmeId,
        UUID programmeVersionId,
        String programmeCode,
        String programmeName,
        String awardName,
        UUID owningAcademicUnitId,
        String owningAcademicUnitName,
        String programmeVersionCode) {
}
