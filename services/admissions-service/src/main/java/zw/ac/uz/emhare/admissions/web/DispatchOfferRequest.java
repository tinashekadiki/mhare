package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record DispatchOfferRequest(
        @NotBlank @Size(max = 40) String deliveryMethodCode,
        @NotBlank @Size(max = 250) String sentTo,
        @Size(max = 200) String providerMessageId) {
}
