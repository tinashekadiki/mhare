package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** @author Tinashe K */
public record SaveQualificationGradeReferenceRequest(
        @NotBlank @Size(max = 30) String level,
        @NotBlank @Size(max = 20) String grade,
        @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal points,
        boolean pass,
        @PositiveOrZero int sortOrder,
        long expectedVersion) {
}
