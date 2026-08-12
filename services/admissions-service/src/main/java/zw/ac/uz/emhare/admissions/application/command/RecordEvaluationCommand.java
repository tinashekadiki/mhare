package zw.ac.uz.emhare.admissions.application.command;

import zw.ac.uz.emhare.admissions.application.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** @author Tinashe K */
public record RecordEvaluationCommand(
        UUID applicationId,
        UUID programmeChoiceId,
        UUID requirementSetId,
        String status,
        BigDecimal rankScore,
        List<Map<String, Object>> missingRequirements,
        Map<String, Object> ruleResults,
        String summary,
        UUID actorUserId) {
}
