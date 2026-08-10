package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
public record MoveApplicationToReviewRequest(
        @NotBlank String reason) {
}
