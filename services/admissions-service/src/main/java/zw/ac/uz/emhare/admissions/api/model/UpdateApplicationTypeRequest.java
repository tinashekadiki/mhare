package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record UpdateApplicationTypeRequest(
        @NotBlank @Size(max = 150) String name,
        boolean requiresEmploymentHistory,
        boolean requiresReferees,
        UUID financeFeeStructureId,
        @Size(max = 50) String financeFeeStructureCode,
        @Size(max = 160) String financeFeeStructureName,
        boolean active,
        @NotBlank @Size(min = 10, max = 1000) String changeReason,
        @Min(0) long expectedVersion) {
}
