package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;

/**
 * @author Tinashe K
 */
public record ApplicationRouteConfigurationSummary(
    UUID applicationTypeId,
    String code,
    String name,
    boolean active,
    boolean readyForActivation,
    List<String> readinessBlockers,
    int activeProgrammeCount,
    List<ProgrammeMappingSummary> programmes,
    List<ApplicationStartOptionsSummary.ApplicationSectionOption> sections,
    int requiredDocumentCount,
    List<DocumentRequirementSummary> documents,
    String feePolicyStatus,
    long version) {

  public record ProgrammeMappingSummary(
      UUID programmeId, String programmeCode, String programmeName) {}

  public record DocumentRequirementSummary(
      String code,
      String name,
      boolean required,
      String captureSectionCode,
      List<String> applicantCategoryCodes,
      int sortOrder) {}
}
