package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record ReturnApplicationToDraftRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
