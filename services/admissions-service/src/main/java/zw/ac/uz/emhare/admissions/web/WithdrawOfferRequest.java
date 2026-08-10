package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;

/** @author Tinashe K */
public record WithdrawOfferRequest(@NotBlank String reason) {
}
