package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;

/** @author Tinashe K */
public record ProgrammeSelectionSnapshot(
        UUID programmeId, UUID programmeVersionId,
        String programmeCode, String programmeName, String awardName,
        UUID owningAcademicUnitId, String owningAcademicUnitName,
        String programmeVersionCode) {

    public static ProgrammeSelectionSnapshot from(AcademicProgrammeOption option) {
        return new ProgrammeSelectionSnapshot(
                option.programmeId(), option.programmeVersionId(),
                option.programmeCode(), option.programmeName(), option.awardName(),
                option.owningAcademicUnitId(), option.owningAcademicUnitName(),
                option.programmeVersionCode());
    }
}
