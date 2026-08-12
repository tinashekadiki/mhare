package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
public record MoveApplicationToReviewRequest(
        @NotBlank String reason) {
}
