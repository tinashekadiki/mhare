package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/** Fail-closed rule grammar and auditable negative eligibility evidence. @author Tinashe K */
class AdvancedRuleEvidenceTest {
  private static final String VERSION = "advanced_rules_v1";
  private final ObjectMapper mapper = new ObjectMapper();
  private final AdvancedAdmissionRuleEvaluator evaluator =
      new AdvancedAdmissionRuleEvaluator(mapper);

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void missingRuleJsonCannotBeActivated(String json) {
    assertThatThrownBy(() -> evaluator.validate(VERSION, json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON is required");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{invalid",
        "[]",
        "null",
        "{}",
        "{\"any\":{}}",
        "{\"any\":[false]}",
        "{\"not\":[]}",
        "{\"all\":[],\"any\":[]}",
        "{\"fact\":\"entryOption.count\"}",
        "{\"operator\":\"eq\"}",
        "{\"value\":1}",
        "{\"fact\":null,\"operator\":\"eq\",\"value\":1}",
        "{\"fact\":\" \",\"operator\":\"eq\",\"value\":1}",
        "{\"fact\":\"entryOption.count\",\"operator\":42,\"value\":1}",
        "{\"fact\":\"entryOption.count\",\"operator\":\" \",\"value\":1}",
        "{\"not\":{\"all\":[]}}"
      })
  void malformedOrAmbiguousRuleNodesCannotBeEvaluated(String json) {
    assertThatThrownBy(() -> evaluator.evaluate(VERSION, json, Map.of("entryOption.count", 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({"eq,3,4", "neq,3,3", "gt,3,3", "gte,2,3", "lt,3,3", "lte,4,3"})
  void failingNumericComparisonsRetainActualAndExpectedEvidence(
      String operator, int actual, int expected) {
    var result = evaluate(operator, expected, actual);
    assertThat(result.satisfied()).isFalse();
    assertThat(result.evidence())
        .containsEntry("actual", actual)
        .containsEntry("expected", expected)
        .containsEntry("missingFact", false)
        .containsEntry("operator", operator)
        .containsEntry("path", "$");
  }

  @ParameterizedTest
  @ValueSource(strings = {"eq", "neq"})
  void numericEqualityNormalizesDecimalScaleWithoutTreatingNonnumericEvidenceAsEqual(
      String operator) {
    boolean equality = operator.equals("eq");
    assertThat(evaluate(operator, new BigDecimal("3.00"), "3").satisfied()).isEqualTo(equality);
    assertThat(evaluate(operator, 3, "not a number").satisfied()).isEqualTo(!equality);
    assertThat(evaluate(operator, "not a number", 3).satisfied()).isEqualTo(!equality);
  }

  @ParameterizedTest
  @ValueSource(strings = {"gt", "gte", "lt", "lte"})
  void numericThresholdsRejectNonnumericFactsInsteadOfSilentlyPassing(String operator) {
    assertThatThrownBy(() -> evaluate(operator, 3, "unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("require numeric values");
  }

  @Test
  void membershipAndContainsReturnFalseWhenNoValueMatches() {
    assertThat(evaluate("in", "LOCAL", "LOCAL").satisfied()).isFalse();
    assertThat(evaluate("in", List.of("LOCAL", "SADC"), "INTERNATIONAL").satisfied()).isFalse();
    assertThat(evaluate("contains", "INTERNATIONAL", List.of("LOCAL", "SADC")).satisfied())
        .isFalse();
    assertThat(evaluate("contains", "OTHER", "LOCAL").satisfied()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nullFactsAndExplicitNullEvidenceAreMarkedMissing(boolean explicitNull) {
    Map<String, Object> facts = explicitNull ? new HashMap<>() : null;
    if (explicitNull) facts.put("entryOption.count", null);
    var result = evaluator.evaluate(VERSION, rule("eq", 0), facts);
    assertThat(result.satisfied()).isFalse();
    assertThat(result.evidence()).containsEntry("missingFact", true).containsEntry("actual", null);
  }

  @ParameterizedTest
  @CsvSource({"all,0,false", "all,1,true", "any,0,false", "any,1,true"})
  void booleanEvaluationRecordsAllChildPathsIncludingFailedConditions(
      String operation, int actual, boolean expected) {
    String json = "{\"" + operation + "\":[" + rule("eq", 1) + "," + rule("gte", 1) + "]}";
    var result = evaluator.evaluate(VERSION, json, Map.of("entryOption.count", actual));
    assertThat(result.satisfied()).isEqualTo(expected);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> children =
        (List<Map<String, Object>>) result.evidence().get("children");
    assertThat(children).hasSize(2);
    assertThat(children.get(0)).containsEntry("path", "$." + operation + "[0]");
    assertThat(children.get(1)).containsEntry("path", "$." + operation + "[1]");
  }

  @Test
  void notConditionRetainsPositiveChildEvidenceWhileDenyingEligibility() {
    var result =
        evaluator.evaluate(
            VERSION, "{\"not\":" + rule("eq", 1) + "}", Map.of("entryOption.count", 1));
    assertThat(result.satisfied()).isFalse();
    assertThat(result.evidence()).containsEntry("type", "not");
  }

  private AdvancedAdmissionRuleEvaluator.RuleResult evaluate(
      String operator, Object expected, Object actual) {
    return evaluator.evaluate(
        VERSION, rule(operator, expected), Map.of("entryOption.count", actual));
  }

  private String rule(String operator, Object expected) {
    return mapper.writeValueAsString(
        Map.of("fact", "entryOption.count", "operator", operator, "value", expected));
  }
}
