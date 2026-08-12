package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** @author Tinashe K */
class AdvancedAdmissionRuleEvaluatorTest {
    private AdvancedAdmissionRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AdvancedAdmissionRuleEvaluator(JsonMapper.builder().build());
    }

    @Test
    void evaluatesNestedAllAnyAndNotConditionsWithAllowListedFacts() {
        String rule = """
                {"all":[
                  {"fact":"applicant.category","operator":"in","value":["LOCAL","SADC"]},
                  {"any":[
                    {"fact":"employment.totalMonths","operator":"gte","value":24},
                    {"fact":"professionalAchievement.count","operator":"gt","value":0}
                  ]},
                  {"not":{"fact":"priorUz.previouslyStudied","operator":"eq","value":true}}
                ]}
                """;

        var result = evaluator.evaluate("advanced_rules_v1", rule, Map.of(
                "applicant.category", "LOCAL",
                "employment.totalMonths", 12,
                "professionalAchievement.count", 2,
                "priorUz.previouslyStudied", false));

        assertTrue(result.satisfied());
        assertTrue((Boolean) result.evidence().get("satisfied"));
    }

    @Test
    void missingFactsFailClosedAndRemainVisibleInEvidence() {
        var result = evaluator.evaluate("advanced_rules_v1",
                "{\"fact\":\"entryOption.count\",\"operator\":\"gte\",\"value\":1}", Map.of());

        assertFalse(result.satisfied());
        assertTrue((Boolean) result.evidence().get("missingFact"));
    }

    @Test
    void rejectsUnknownFactsOperatorsVersionsAndMalformedBooleanNodes() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(
                "advanced_rules_v2", "{\"fact\":\"entryOption.count\",\"operator\":\"eq\",\"value\":1}"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(
                "advanced_rules_v1", "{\"fact\":\"runtime.code\",\"operator\":\"eq\",\"value\":1}"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(
                "advanced_rules_v1", "{\"fact\":\"entryOption.count\",\"operator\":\"exec\",\"value\":1}"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(
                "advanced_rules_v1", "{\"all\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(
                "advanced_rules_v1", "{\"not\":{\"fact\":\"entryOption.count\",\"operator\":\"eq\",\"value\":0},\"script\":\"deny\"}"));
    }

    @ParameterizedTest
    @CsvSource({
        "eq, 3, 3", "neq, 3, 4", "gt, 4, 3", "gte, 3, 3",
        "lt, 2, 3", "lte, 3, 3"
    })
    void evaluatesEveryScalarComparisonOperator(String operator, int actual, int expected) {
        var result = evaluator.evaluate("advanced_rules_v1",
                "{\"fact\":\"qualification.DEGREE.count\",\"operator\":\"" + operator
                        + "\",\"value\":" + expected + "}",
                Map.of("qualification.DEGREE.count", actual));

        assertTrue(result.satisfied());
    }

    @Test
    void evaluatesMembershipAndCollectionOrTextContainment() {
        assertTrue(evaluator.evaluate("advanced_rules_v1",
                "{\"fact\":\"applicant.category\",\"operator\":\"in\",\"value\":[\"LOCAL\",\"SADC\"]}",
                Map.of("applicant.category", "SADC")).satisfied());
        assertTrue(evaluator.evaluate("advanced_rules_v1",
                "{\"fact\":\"applicant.category\",\"operator\":\"contains\",\"value\":\"ADC\"}",
                Map.of("applicant.category", "SADC")).satisfied());
        assertTrue(evaluator.evaluate("advanced_rules_v1",
                "{\"fact\":\"applicant.category\",\"operator\":\"contains\",\"value\":\"SADC\"}",
                Map.of("applicant.category", java.util.List.of("LOCAL", "SADC"))).satisfied());
    }
}
