package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @author Tinashe K */
public record LinkApplicationDocumentRequest(
        @NotNull UUID documentId,
        @NotBlank @Size(max = 80) String requirementCode) {
}
