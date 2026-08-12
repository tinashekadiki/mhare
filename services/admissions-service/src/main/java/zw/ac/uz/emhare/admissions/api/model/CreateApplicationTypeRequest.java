package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record CreateApplicationTypeRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must use letters, numbers, hyphens, or underscores")
        String code,
        @NotBlank @Size(max = 150) String name,
        boolean requiresEmploymentHistory,
        boolean requiresReferees,
        UUID financeFeeStructureId,
        @Size(max = 50) String financeFeeStructureCode,
        @Size(max = 160) String financeFeeStructureName,
        boolean active) {
}
