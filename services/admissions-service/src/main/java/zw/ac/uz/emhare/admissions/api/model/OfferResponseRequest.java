package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record OfferResponseRequest(
        @NotBlank String response,
        @Size(max = 1000) String notes) {
}
