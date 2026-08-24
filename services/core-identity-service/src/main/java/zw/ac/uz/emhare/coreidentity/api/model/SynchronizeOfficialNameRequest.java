package zw.ac.uz.emhare.coreidentity.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Admissions-approved official-name synchronization request. @author Tinashe K */
public record SynchronizeOfficialNameRequest(
    @NotNull UUID sourceRequestId,
    @NotNull UUID sourceApplicationId,
    @NotNull UUID sourceDocumentId,
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 150) String middleNames,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Size(min = 10, max = 1000) String approvalReason) {}
