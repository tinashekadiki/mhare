package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Safe interpreter for the versioned admissions rule grammar. @author Tinashe K */
@Component
public class AdvancedAdmissionRuleEvaluator {

    public static final String SUPPORTED_VERSION = "advanced_rules_v1";
    private static final Set<String> ALLOWED_FACTS = Set.of(
            "applicant.category",
            "qualification.O_LEVEL.count",
            "qualification.A_LEVEL.count",
            "qualification.DIPLOMA.count",
            "qualification.DEGREE.count",
            "qualification.PROFESSIONAL.count",
            "qualification.OTHER.count",
            "employment.totalMonths",
            "professionalAchievement.count",
            "priorUz.previouslyStudied",
            "entryOption.count");
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "eq", "neq", "gt", "gte", "lt", "lte", "in", "contains");

    private final ObjectMapper objectMapper;

    public AdvancedAdmissionRuleEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleResult evaluate(String version, String ruleJson, Map<String, Object> facts) {
        Map<String, Object> rule = parseAndValidate(version, ruleJson);
        return evaluateNode(rule, facts == null ? Map.of() : facts, "$" );
    }

    public void validate(String version, String ruleJson) {
        parseAndValidate(version, ruleJson);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAndValidate(String version, String ruleJson) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported advanced admissions rule version: " + version);
        }
        if (ruleJson == null || ruleJson.isBlank()) {
            throw new IllegalArgumentException("Advanced admissions rule JSON is required.");
        }
        try {
            Object parsed = objectMapper.readValue(ruleJson, Object.class);
            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                throw new IllegalArgumentException("Advanced admissions rule root must be an object.");
            }
            Map<String, Object> rule = (Map<String, Object>) parsedMap;
            validateNode(rule, "$");
            return rule;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Advanced admissions rule JSON is malformed.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(Map<String, Object> node, String path) {
        List<String> booleanKeys = List.of("all", "any", "not").stream().filter(node::containsKey).toList();
        boolean condition = node.containsKey("fact") || node.containsKey("operator") || node.containsKey("value");
        if (booleanKeys.size() + (condition ? 1 : 0) != 1) {
            throw new IllegalArgumentException(path + " must contain exactly one boolean expression or fact condition.");
        }
        if (condition) {
            if (!node.keySet().equals(Set.of("fact", "operator", "value"))) {
                throw new IllegalArgumentException(path + " condition contains unsupported properties.");
            }
            String fact = requiredString(node.get("fact"), path + ".fact");
            String operator = requiredString(node.get("operator"), path + ".operator");
            if (!ALLOWED_FACTS.contains(fact)) throw new IllegalArgumentException("Unsupported advanced-rule fact: " + fact);
            if (!ALLOWED_OPERATORS.contains(operator)) throw new IllegalArgumentException("Unsupported advanced-rule operator: " + operator);
            if (!node.containsKey("value")) throw new IllegalArgumentException(path + ".value is required.");
            return;
        }
        String key = booleanKeys.getFirst();
        if (!node.keySet().equals(Set.of(key))) {
            throw new IllegalArgumentException(path + " boolean expression contains unsupported properties.");
        }
        Object value = node.get(key);
        if ("not".equals(key)) {
            if (!(value instanceof Map<?, ?> child)) throw new IllegalArgumentException(path + ".not must be an object.");
            validateNode((Map<String, Object>) child, path + ".not");
            return;
        }
        if (!(value instanceof List<?> children) || children.isEmpty()) {
            throw new IllegalArgumentException(path + "." + key + " must be a non-empty array.");
        }
        for (int index = 0; index < children.size(); index++) {
            if (!(children.get(index) instanceof Map<?, ?> child)) {
                throw new IllegalArgumentException(path + "." + key + "[" + index + "] must be an object.");
            }
            validateNode((Map<String, Object>) child, path + "." + key + "[" + index + "]");
        }
    }

    @SuppressWarnings("unchecked")
    private RuleResult evaluateNode(Map<String, Object> node, Map<String, Object> facts, String path) {
        if (node.containsKey("all") || node.containsKey("any")) {
            String operation = node.containsKey("all") ? "all" : "any";
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get(operation);
            List<RuleResult> childResults = new ArrayList<>();
            for (int index = 0; index < children.size(); index++) {
                childResults.add(evaluateNode(children.get(index), facts, path + "." + operation + "[" + index + "]"));
            }
            boolean satisfied = "all".equals(operation)
                    ? childResults.stream().allMatch(RuleResult::satisfied)
                    : childResults.stream().anyMatch(RuleResult::satisfied);
            return new RuleResult(satisfied, evidence(path, operation, satisfied, childResults));
        }
        if (node.containsKey("not")) {
            RuleResult child = evaluateNode((Map<String, Object>) node.get("not"), facts, path + ".not");
            return new RuleResult(!child.satisfied(), evidence(path, "not", !child.satisfied(), List.of(child)));
        }
        String fact = String.valueOf(node.get("fact"));
        String operator = String.valueOf(node.get("operator"));
        Object expected = node.get("value");
        Object actual = facts.get(fact);
        boolean missing = !facts.containsKey(fact) || actual == null;
        boolean satisfied = !missing && compare(actual, operator, expected);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("path", path);
        evidence.put("type", "condition");
        evidence.put("fact", fact);
        evidence.put("operator", operator);
        evidence.put("expected", expected);
        evidence.put("actual", actual);
        evidence.put("missingFact", missing);
        evidence.put("satisfied", satisfied);
        return new RuleResult(satisfied, evidence);
    }

    private boolean compare(Object actual, String operator, Object expected) {
        return switch (operator) {
            case "eq" -> valuesEqual(actual, expected);
            case "neq" -> !valuesEqual(actual, expected);
            case "gt" -> compareNumbers(actual, expected) > 0;
            case "gte" -> compareNumbers(actual, expected) >= 0;
            case "lt" -> compareNumbers(actual, expected) < 0;
            case "lte" -> compareNumbers(actual, expected) <= 0;
            case "in" -> expected instanceof Collection<?> values && values.stream().anyMatch(value -> valuesEqual(actual, value));
            case "contains" -> actual instanceof Collection<?> values
                    ? values.stream().anyMatch(value -> valuesEqual(value, expected))
                    : String.valueOf(actual).contains(String.valueOf(expected));
            default -> false;
        };
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number || right instanceof Number) {
            try { return compareNumbers(left, right) == 0; } catch (IllegalArgumentException ignored) { return false; }
        }
        return java.util.Objects.equals(left, right);
    }

    private int compareNumbers(Object left, Object right) {
        try {
            return new BigDecimal(String.valueOf(left)).compareTo(new BigDecimal(String.valueOf(right)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Numeric advanced-rule operators require numeric values.", exception);
        }
    }

    private Map<String, Object> evidence(String path, String type, boolean satisfied, List<RuleResult> children) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("path", path);
        evidence.put("type", type);
        evidence.put("satisfied", satisfied);
        evidence.put("children", children.stream().map(RuleResult::evidence).toList());
        return evidence;
    }

    private String requiredString(Object value, String label) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return text.trim();
    }

    public record RuleResult(boolean satisfied, Map<String, Object> evidence) { }
}
