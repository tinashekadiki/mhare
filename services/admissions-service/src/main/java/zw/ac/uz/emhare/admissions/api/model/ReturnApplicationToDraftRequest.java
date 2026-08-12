package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record ReturnApplicationToDraftRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
