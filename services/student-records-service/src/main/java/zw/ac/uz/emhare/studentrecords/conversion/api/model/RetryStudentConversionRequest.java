package zw.ac.uz.emhare.studentrecords.conversion.api.model;

import zw.ac.uz.emhare.studentrecords.conversion.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record RetryStudentConversionRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
