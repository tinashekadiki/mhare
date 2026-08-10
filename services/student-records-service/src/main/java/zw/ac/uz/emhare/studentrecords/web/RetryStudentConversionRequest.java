package zw.ac.uz.emhare.studentrecords.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record RetryStudentConversionRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
