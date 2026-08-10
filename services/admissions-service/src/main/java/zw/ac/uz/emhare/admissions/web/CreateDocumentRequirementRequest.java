package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record CreateDocumentRequirementRequest(
        @NotBlank @Size(max = 80) String requirementCode,
        @NotBlank @Size(max = 150) String requirementName,
        boolean required,
        @Min(1) int sortOrder) {
}
