package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record OfferResponseRequest(
        @NotBlank String response,
        @Size(max = 1000) String notes) {
}
