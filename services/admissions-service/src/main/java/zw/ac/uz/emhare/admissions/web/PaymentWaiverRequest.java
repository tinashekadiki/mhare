package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;

public record PaymentWaiverRequest(@NotBlank String reason) {
}
