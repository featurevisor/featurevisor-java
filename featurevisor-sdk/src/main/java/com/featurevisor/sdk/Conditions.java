package com.featurevisor.sdk;

import com.featurevisor.sdk.Condition;
import com.featurevisor.sdk.Operator;
import java.util.Map;
import java.util.List;
import java.util.Date;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Conditions utility for Featurevisor SDK
 * Provides condition matching functionality
 */
public class Conditions {
    /**
     * Functional interface for getting regex patterns.
     */
    @FunctionalInterface
    interface GetRegex {
        Pattern getRegex(String regexString, String regexFlags);
    }

    /**
     * Check if a path exists in a context object
     * @param context The context object
     * @param path The dot-separated path
     * @return True if the path exists
     */
    private static boolean pathExists(Map<String, Object> context, String path) {
        if (path.indexOf(".") == -1) {
            return context.containsKey(path);
        }

        String[] keys = path.split("\\.");
        Object current = context;

        for (String key : keys) {
            if (!(current instanceof Map)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) current;
            if (!map.containsKey(key)) {
                return false;
            }
            current = map.get(key);
        }

        return true;
    }

    /**
     * Check if a condition is matched given a context
     * @param condition The condition to check
     * @param context The context to check against
     * @param getRegex Function to get regex patterns
     * @return True if the condition is matched
     */
    static boolean conditionIsMatched(
            Condition condition,
            Map<String, Object> context,
            GetRegex getRegex) {

        if (condition == null) {
            return false;
        }

        // Handle string condition (wildcard matching)
        if (condition.isStringCondition()) {
            String stringCondition = condition.getStringCondition();
            if ("*".equals(stringCondition)) {
                return true;
            }
            return false;
        }

        // Handle logical operators
        if (condition.isAndCondition()) {
            List<Condition> andConditions = condition.getAnd();
            if (andConditions == null || andConditions.isEmpty()) {
                return true;
            }
            for (Condition subCondition : andConditions) {
                if (!conditionIsMatched(subCondition, context, getRegex)) {
                    return false;
                }
            }
            return true;
        }

        if (condition.isOrCondition()) {
            List<Condition> orConditions = condition.getOr();
            if (orConditions == null || orConditions.isEmpty()) {
                return false;
            }
            for (Condition subCondition : orConditions) {
                if (conditionIsMatched(subCondition, context, getRegex)) {
                    return true;
                }
            }
            return false;
        }

        if (condition.isNotCondition()) {
            List<Condition> notConditions = condition.getNot();
            if (notConditions == null || notConditions.isEmpty()) {
                return false;
            }
            for (Condition subCondition : notConditions) {
                if (!conditionIsMatched(subCondition, context, getRegex)) {
                    return true;
                }
            }
            return false;
        }

        // Handle plain condition
        if (!condition.isPlainCondition()) {
            return false;
        }

        String attribute = condition.getAttribute();
        Operator operator = condition.getOperator();
        Object value = condition.getValue();
        String regexFlags = condition.getRegexFlags();

        if (attribute == null || operator == null) {
            return false;
        }

        Object contextValue = ContextUtils.getValueFromContext(context, attribute);

        switch (operator) {
            case EQUALS:
                return pathExists(context, attribute) && equals(contextValue, value);
            case NOT_EQUALS:
                return !pathExists(context, attribute) || !equals(contextValue, value);
            case IN:
                return pathExists(context, attribute) && in(contextValue, value);
            case NOT_IN:
                // Match PHP implementation: check if value is array and context value is string/numeric/null
                if (value instanceof List &&
                    (contextValue instanceof String || contextValue instanceof Number || contextValue == null) &&
                    pathExists(context, attribute)) {
                    return !in(contextValue, value);
                }
                return false;
            case CONTAINS:
                return contains(contextValue, value);
            case NOT_CONTAINS:
                return contextValue instanceof String && value instanceof String && !contains(contextValue, value);
            case STARTS_WITH:
                return startsWith(contextValue, value);
            case ENDS_WITH:
                return endsWith(contextValue, value);
            case GREATER_THAN:
                return greaterThan(contextValue, value);
            case GREATER_THAN_OR_EQUALS:
                return greaterThanOrEquals(contextValue, value);
            case LESS_THAN:
                return lessThan(contextValue, value);
            case LESS_THAN_OR_EQUALS:
                return lessThanOrEquals(contextValue, value);
            case EXISTS:
                return pathExists(context, attribute);
            case NOT_EXISTS:
                return !pathExists(context, attribute);
            case MATCHES:
                return matches(contextValue, value, regexFlags, getRegex);
            case NOT_MATCHES:
                return contextValue instanceof String && value instanceof String && !matches(contextValue, value, regexFlags, getRegex);
            case INCLUDES:
                return includes(contextValue, value);
            case NOT_INCLUDES:
                return contextValue instanceof List && isPrimitive(value) && !includes(contextValue, value);
            case SEMVER_EQUALS:
                return semverEquals(contextValue, value);
            case SEMVER_NOT_EQUALS:
                return contextValue instanceof String && value instanceof String && !semverEquals(contextValue, value);
            case SEMVER_GREATER_THAN:
                return semverGreaterThan(contextValue, value);
            case SEMVER_GREATER_THAN_OR_EQUALS:
                return semverGreaterThanOrEquals(contextValue, value);
            case SEMVER_LESS_THAN:
                return semverLessThan(contextValue, value);
            case SEMVER_LESS_THAN_OR_EQUALS:
                return semverLessThanOrEquals(contextValue, value);
            case BEFORE:
                return before(contextValue, value);
            case AFTER:
                return after(contextValue, value);
            default:
                return false;
        }
    }

    /**
     * Check if all conditions are matched given a context.
     * This mirrors the JavaScript SDK's narrow root helper without exposing the
     * instance evaluation data provider.
     */
    public static boolean allConditionsAreMatched(Object conditions, Map<String, Object> context) {
        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2");
        datafile.setRevision("matcher");
        datafile.setFeatures(new java.util.HashMap<>());
        datafile.setSegments(new java.util.HashMap<>());

        return new InstanceEvaluationDataProvider(new InstanceEvaluationDataProvider.InstanceEvaluationDataProviderOptions()
            .datafile(datafile)
            .diagnostics(DiagnosticReporter.createDiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions())))
            .allConditionsAreMatched(conditions, context);
    }

    private static boolean equals(Object contextValue, Object conditionValue) {
        if (contextValue == null && conditionValue == null) {
            return true;
        }
        if (contextValue == null || conditionValue == null) {
            return false;
        }
        if (contextValue instanceof Number && conditionValue instanceof Number) {
            double left = ((Number) contextValue).doubleValue();
            double right = ((Number) conditionValue).doubleValue();
            return !Double.isNaN(left) && !Double.isNaN(right) && left == right;
        }
        if (contextValue instanceof String && conditionValue instanceof String) {
            return contextValue.equals(conditionValue);
        }
        if (contextValue instanceof Boolean && conditionValue instanceof Boolean) {
            return contextValue.equals(conditionValue);
        }
        return false;
    }

    private static boolean in(Object contextValue, Object conditionValue) {
        if (!(conditionValue instanceof List)) {
            return false;
        }

        // Handle null context value
        if (contextValue == null) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) conditionValue;
            return values.stream().anyMatch(item -> equals(item, null));
        }

        // Handle string, numeric, or null context values
        if (contextValue instanceof String || contextValue instanceof Number || contextValue == null) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) conditionValue;
            return values.stream().anyMatch(item -> equals(item, contextValue));
        }

        return false;
    }

    private static boolean contains(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return ((String) contextValue).contains((String) conditionValue);
    }

    private static boolean startsWith(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return ((String) contextValue).startsWith((String) conditionValue);
    }

    private static boolean endsWith(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return ((String) contextValue).endsWith((String) conditionValue);
    }

    private static boolean greaterThan(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof Number) || !(conditionValue instanceof Number)) {
            return false;
        }
        return ((Number) contextValue).doubleValue() > ((Number) conditionValue).doubleValue();
    }

    private static boolean greaterThanOrEquals(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof Number) || !(conditionValue instanceof Number)) {
            return false;
        }
        return ((Number) contextValue).doubleValue() >= ((Number) conditionValue).doubleValue();
    }

    private static boolean lessThan(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof Number) || !(conditionValue instanceof Number)) {
            return false;
        }
        return ((Number) contextValue).doubleValue() < ((Number) conditionValue).doubleValue();
    }

    private static boolean lessThanOrEquals(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof Number) || !(conditionValue instanceof Number)) {
            return false;
        }
        return ((Number) contextValue).doubleValue() <= ((Number) conditionValue).doubleValue();
    }

    private static boolean matches(Object contextValue, Object conditionValue, String regexFlags, GetRegex getRegex) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }

        Pattern regex = getRegex.getRegex((String) conditionValue, regexFlags != null ? regexFlags : "");
        if (regex == null) {
            return false;
        }

        return regex.matcher((String) contextValue).find();
    }

    private static boolean includes(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof List) || !isPrimitive(conditionValue)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) contextValue;
        return list.stream().anyMatch(item -> equals(item, conditionValue));
    }

    private static boolean isPrimitive(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static boolean semverEquals(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return CompareVersions.compareVersions((String) contextValue, (String) conditionValue) == 0;
    }

    private static boolean semverGreaterThan(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return CompareVersions.compareVersions((String) contextValue, (String) conditionValue) == 1;
    }

    private static boolean semverGreaterThanOrEquals(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return CompareVersions.compareVersions((String) contextValue, (String) conditionValue) >= 0;
    }

    private static boolean semverLessThan(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return CompareVersions.compareVersions((String) contextValue, (String) conditionValue) == -1;
    }

    private static boolean semverLessThanOrEquals(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof String) || !(conditionValue instanceof String)) {
            return false;
        }
        return CompareVersions.compareVersions((String) contextValue, (String) conditionValue) <= 0;
    }

    private static boolean before(Object contextValue, Object conditionValue) {
        Instant contextDate = parseDate(contextValue);
        Instant conditionDate = parseDate(conditionValue);

        if (contextDate == null || conditionDate == null) {
            return false;
        }

        return contextDate.isBefore(conditionDate);
    }

    private static boolean after(Object contextValue, Object conditionValue) {
        Instant contextDate = parseDate(contextValue);
        Instant conditionDate = parseDate(conditionValue);

        if (contextDate == null || conditionDate == null) {
            return false;
        }

        return contextDate.isAfter(conditionDate);
    }

    private static Instant parseDate(Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        if (value instanceof String) {
            try {
                return OffsetDateTime.parse((String) value).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}
