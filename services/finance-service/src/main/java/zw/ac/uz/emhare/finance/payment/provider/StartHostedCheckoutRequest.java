package zw.ac.uz.emhare.finance.payment.provider;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record StartHostedCheckoutRequest(
        @NotBlank @Email @Size(max = 40) String emailAddress) {
}
