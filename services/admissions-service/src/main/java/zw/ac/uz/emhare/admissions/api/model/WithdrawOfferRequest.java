package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;

/** @author Tinashe K */
public record WithdrawOfferRequest(@NotBlank String reason) {
}
