package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** @author Tinashe K */
public record RecordEvaluationRequest(
        @NotNull UUID requirementSetId,
        @NotBlank String status,
        BigDecimal rankScore,
        List<Map<String, Object>> missingRequirements,
        Map<String, Object> ruleResults,
        @NotBlank String summary) {
}
