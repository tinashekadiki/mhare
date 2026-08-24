package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Atomic route-governance request. @author Tinashe K */
public record ConfigureApplicationRouteRequest(
    @NotNull @Size(max = 500) List<@Valid ProgrammeMappingInput> programmes,
    @NotNull @Size(min = 1, max = 30) List<@Valid SectionInput> sections,
    @NotNull @Size(min = 1, max = 100) List<@Valid DocumentInput> documents,
    boolean feeFree,
    @Size(max = 1000) String feeFreeReason,
    boolean activate,
    @NotBlank @Size(min = 10, max = 1000) String changeReason,
    @Min(0) long expectedVersion) {

  public record ProgrammeMappingInput(
      @NotNull UUID programmeId,
      @NotBlank @Size(max = 50) String programmeCode,
      @NotBlank @Size(max = 200) String programmeName) {}

  public record SectionInput(
      @NotBlank @Size(max = 60) String code,
      @NotBlank @Size(max = 150) String name,
      boolean required,
      boolean repeatable,
      @Min(0) int minimumRecords,
      @Min(1) int sortOrder) {}

  public record DocumentInput(
      @NotBlank @Size(max = 60) String code,
      @NotBlank @Size(max = 160) String name,
      boolean required,
      @NotBlank @Size(max = 60) String captureSectionCode,
      @NotNull @Size(max = 4) List<@NotBlank @Size(max = 30) String> applicantCategoryCodes,
      @Min(1) int sortOrder) {}
}
