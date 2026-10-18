package com.featurevisor.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.CompareVersions;
import com.featurevisor.sdk.Feature;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class VariableChecks {
    interface Resolver { Resolution resolve(Map<String, Object> reference); }
    interface FeatureResolver { Resolution resolve(String feature, String getter); }
    interface SegmentResolver { Resolution resolve(String segment); }
    interface SegmentValidator { String validate(String segment); }

    static final class Resolution {
        final Object value;
        final String error;
        Resolution(Object value, String error) { this.value = value; this.error = error; }
        static Resolution value(Object value) { return new Resolution(value, null); }
        static Resolution error(String error) { return new Resolution(null, error); }
    }

    static final class CheckFailure {
        final String message;
        final List<Object> path;
        final Object expected;
        final Object actual;
        final Map<String, Object> details;

        CheckFailure(String message, List<Object> path, Object expected, Object actual, Map<String, Object> details) {
            this.message = message;
            this.path = path;
            this.expected = expected;
            this.actual = actual;
            this.details = details;
        }
    }

    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
        "equals", "notEquals", "exists", "notExists", "isNull", "notNull", "empty", "notEmpty",
        "greaterThan", "greaterThanOrEquals", "lessThan", "lessThanOrEquals",
        "contains", "notContains", "startsWith", "endsWith", "semverEquals",
        "semverNotEquals", "semverGreaterThan", "semverGreaterThanOrEquals",
        "semverLessThan", "semverLessThanOrEquals", "before", "after", "includes",
        "notIncludes", "includesAny", "notIncludesAny", "matches", "notMatches", "in", "notIn"
    ));
    private static final Set<String> UNSAFE = new HashSet<>(Arrays.asList(
        "*", "__proto__", "prototype", "constructor"
    ));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern DATE = Pattern.compile(
        "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|([+-])(\\d{2}):(\\d{2}))$"
    );

    private final Resolver resolver;
    private final FeatureResolver featureResolver;
    private final SegmentResolver segmentResolver;
    private final SegmentValidator segmentValidator;
    private String failedPath;
    private String failedDetail;

    VariableChecks(Resolver resolver) {
        this(resolver,
            (feature, getter) -> Resolution.error("feature expectations are unavailable"),
            segment -> Resolution.error("segment \"" + segment + "\" definition is missing"),
            segment -> "segment \"" + segment + "\" definition is missing");
    }

    VariableChecks(Resolver resolver, FeatureResolver featureResolver) {
        this(resolver, featureResolver,
            segment -> Resolution.error("segment \"" + segment + "\" definition is missing"),
            segment -> "segment \"" + segment + "\" definition is missing");
    }

    VariableChecks(Resolver resolver, FeatureResolver featureResolver, SegmentResolver segmentResolver,
            SegmentValidator segmentValidator) {
        this.resolver = resolver;
        this.featureResolver = featureResolver;
        this.segmentResolver = segmentResolver;
        this.segmentValidator = segmentValidator;
    }

    List<String> evaluate(Object checks) {
        List<String> messages = new ArrayList<>();
        for (CheckFailure failure : evaluateStructured(checks)) messages.add(failure.message);
        return messages;
    }

    static List<CheckFailure> preflight(Object checks, String ownerVariable,
            DatafileContent datafile, Map<String, Object> segmentDefinitions, String path) {
        if (!(checks instanceof List) || ((List<?>) checks).isEmpty()) {
            return Collections.singletonList(new CheckFailure(
                path + " error: checks must be a nonempty array", Arrays.asList("checks"),
                "valid checks", checks, null));
        }
        VariableChecks validator = new VariableChecks(reference -> Resolution.error("not evaluated"));
        List<?> list = (List<?>) checks;
        try {
            validator.validateList(list, path);
        } catch (CheckException error) {
            return Collections.singletonList(new CheckFailure(
                error.path + " error: " + error.getMessage(), validator.topLevelPath(error.path),
                "valid checks", error.getMessage(), null));
        }
        try {
            for (int i = 0; i < list.size(); i++) {
                validator.validateReferences(validator.map(list.get(i), path + "[" + i + "]", "check"),
                    ownerVariable, datafile, segmentDefinitions, path + "[" + i + "]");
            }
            return Collections.emptyList();
        } catch (CheckException error) {
            return Collections.singletonList(new CheckFailure(
                error.path + " error: " + error.getMessage(), validator.topLevelPath(error.path),
                "valid check references", error.getMessage(), null));
        }
    }

    private void validateReferences(Map<String, Object> value, String ownerVariable,
            DatafileContent datafile, Map<String, Object> segmentDefinitions, String path) {
        for (String group : Arrays.asList("and", "or", "not")) {
            if (value.get(group) instanceof List) {
                List<?> children = (List<?>) value.get(group);
                for (int i = 0; i < children.size(); i++) {
                    validateReferences(map(children.get(i), path + "." + group + "[" + i + "]", "check"),
                        ownerVariable, datafile, segmentDefinitions, path + "." + group + "[" + i + "]");
                }
                return;
            }
        }
        if (value.get("segment") instanceof String) {
            String segment = (String) value.get("segment");
            Object raw = segmentDefinitions.get(segment);
            if (!(raw instanceof Map)) fail(path, "segment \"" + segment + "\" definition is missing");
            if (!((Map<?, ?>) raw).containsKey("conditions")) {
                fail(path, "segment \"" + segment + "\" definition has no conditions");
            }
            String error = SegmentEvaluations.validate(segment, datafile, segmentDefinitions);
            if (error != null) fail(path, error);
            return;
        }
        if (value.containsKey("enabled") || value.containsKey("variation")) {
            String feature = (String) value.get("feature");
            if (datafile.getFeatures() == null || !datafile.getFeatures().containsKey(feature)) {
                fail(path, "feature \"" + feature + "\" is missing from the selected datafile");
            }
            return;
        }
        validateReferenceMembership(value, ownerVariable, datafile, path);
        if (value.get("compareTo") instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> compareTo = (Map<String, Object>) value.get("compareTo");
            validateReferenceMembership(compareTo, ownerVariable, datafile, path + ".compareTo");
        }
    }

    private void validateReferenceMembership(Map<String, Object> reference, String ownerVariable,
            DatafileContent datafile, String path) {
        String variable = reference.get("variable") instanceof String
            ? (String) reference.get("variable") : ownerVariable;
        String featureKey = reference.get("feature") instanceof String
            ? (String) reference.get("feature") : null;
        if (variable == null) fail(path, "variable is required");
        if (featureKey != null) {
            Feature feature = datafile.getFeatures() == null ? null : datafile.getFeatures().get(featureKey);
            if (feature == null) fail(path, "feature \"" + featureKey + "\" is missing from the selected datafile");
            if (variable == null || feature.getVariablesSchema() == null
                    || !feature.getVariablesSchema().containsKey(variable)) {
                fail(path, "variable \"" + variable + "\" is missing from feature \""
                    + featureKey + "\" in the selected datafile");
            }
        } else if (variable != null && (datafile.getVariables() == null
                || !datafile.getVariables().containsKey(variable))) {
            fail(path, "global variable \"" + variable + "\" is missing from the selected datafile");
        }
    }

    List<CheckFailure> evaluateStructured(Object checks) {
        if (!(checks instanceof List) || ((List<?>) checks).isEmpty()) {
            return Collections.singletonList(new CheckFailure(
                "checks error: checks must be a nonempty array", Arrays.asList("checks"), "valid checks", checks, null));
        }
        List<?> list = (List<?>) checks;
        try {
            validateList(list, "checks");
            validateSegmentReferences(list, "checks");
        } catch (CheckException error) {
            return Collections.singletonList(new CheckFailure(
                error.path + " error: " + error.getMessage(), topLevelPath(error.path), "valid checks", error.getMessage(), null));
        }
        List<CheckFailure> failures = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            failedPath = null;
            failedDetail = null;
            List<Map<String, Object>> evaluated = new ArrayList<>();
            Map<String, Object> check = map(list.get(i), "checks[" + i + "]", "check");
            try {
                if (!node(list.get(i), "checks[" + i + "]", Arrays.asList(i), evaluated)) {
                    String path = failedPath == null ? "checks[" + i + "]" : failedPath;
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("checkIndex", i);
                    if (check.get("key") != null) details.put("checkKey", check.get("key"));
                    if (check.get("description") != null) details.put("checkDescription", check.get("description"));
                    details.put("evaluated", evaluated);
                    failures.add(new CheckFailure(path + " failed: "
                        + (failedDetail == null ? "check did not match" : failedDetail),
                        Arrays.asList("checks", i), true, false, details));
                }
            } catch (CheckException error) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("checkIndex", i);
                if (check.get("key") != null) details.put("checkKey", check.get("key"));
                if (check.get("description") != null) details.put("checkDescription", check.get("description"));
                details.put("evaluated", evaluated);
                failures.add(new CheckFailure(error.path + " error: " + error.getMessage(),
                    Arrays.asList("checks", i), "successful comparison", error.getMessage(), details));
            }
        }
        return failures;
    }

    private List<Object> topLevelPath(String path) {
        int open = path.indexOf('[');
        int close = path.indexOf(']', open + 1);
        if (open < 0 || close < 0) return Arrays.asList("checks");
        try {
            return Arrays.asList("checks", Integer.parseInt(path.substring(open + 1, close)));
        } catch (NumberFormatException ignored) {
            return Arrays.asList("checks");
        }
    }

    private void validateList(List<?> list, String path) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Map<String, Object> value = map(list.get(i), itemPath, "check");
            validateMetadata(value, itemPath);
            if (value.get("key") instanceof String && !keys.add((String) value.get("key"))) {
                fail(itemPath, "duplicate sibling key \"" + value.get("key") + "\"");
            }
            validate(value, itemPath);
        }
    }

    private void validateMetadata(Map<String, Object> value, String path) {
        if (value.containsKey("key")
                && (!(value.get("key") instanceof String) || ((String) value.get("key")).isEmpty())) {
            fail(path, "key must be a nonempty string");
        }
        if (value.containsKey("description") && !(value.get("description") instanceof String)) {
            fail(path, "description must be a string");
        }
    }

    private void validate(Object raw, String path) {
        Map<String, Object> value = map(raw, path, "check");
        validateMetadata(value, path);
        List<String> groups = groups(value);
        if (!groups.isEmpty()) {
            Set<String> allowed = new HashSet<>(Arrays.asList(groups.get(0), "key", "description"));
            if (groups.size() != 1 || !allowed.containsAll(value.keySet())) fail(path, "group must contain exactly one operator");
            String op = groups.get(0);
            Object childrenValue = value.get(op);
            if (!(childrenValue instanceof List) || ((List<?>) childrenValue).isEmpty()) {
                fail(path, op + " group must be a nonempty array");
            }
            List<?> children = (List<?>) childrenValue;
            validateList(children, path + "." + op);
            return;
        }
        if (value.containsKey("segment") || value.containsKey("matches")) {
            validateSegmentExpectation(value, path);
            return;
        }
        if (value.containsKey("enabled") || value.containsKey("variation")) {
            validateFeatureExpectation(value, path);
            return;
        }
        Set<String> allowed = new HashSet<>(Arrays.asList(
            "variable", "feature", "path", "operator", "value", "compareTo", "regexFlags", "key", "description"
        ));
        for (String key : value.keySet()) if (!allowed.contains(key)) fail(path, "unknown property \"" + key + "\"");
        Object operatorValue = value.get("operator");
        if (!(operatorValue instanceof String) || !OPERATORS.contains(operatorValue)) {
            fail(path, "unknown or missing operator \"" + String.valueOf(operatorValue) + "\"");
        }
        String operator = (String) operatorValue;
        boolean unary = Arrays.asList("exists", "notExists", "isNull", "notNull", "empty", "notEmpty").contains(operator);
        boolean hasValue = value.containsKey("value");
        boolean hasReference = value.containsKey("compareTo");
        if (unary ? hasValue || hasReference : hasValue == hasReference) {
            fail(path, unary ? operator + " accepts no operand" : "exactly one of value or compareTo is required");
        }
        validateReference(value, path, false);
        if (hasReference) validateReference(map(value.get("compareTo"), path, "compareTo"), path, true);
        if (value.containsKey("regexFlags") && !operator.equals("matches") && !operator.equals("notMatches")) {
            fail(path, "regexFlags is only valid for regex operators");
        }
        if (value.containsKey("regexFlags") && value.get("regexFlags") == null) fail(path, "regexFlags must be a string");
        if (value.containsKey("regexFlags")) regexFlags(value.get("regexFlags"), path);
        if (hasValue) validateLiteral(operator, value.get("value"), value.get("regexFlags"), path);
    }

    private void validateSegmentExpectation(Map<String, Object> value, String path) {
        Set<String> allowed = new HashSet<>(Arrays.asList("segment", "matches", "key", "description"));
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) fail(path, "segment check has unexpected property \"" + key + "\"");
        }
        if (!(value.get("segment") instanceof String) || ((String) value.get("segment")).trim().isEmpty()) {
            fail(path, "segment must be a nonempty string");
        }
        if (!(value.get("matches") instanceof Boolean)) {
            fail(path, "segment \"" + value.get("segment") + "\" matches must be a boolean");
        }
    }

    private void validateSegmentReferences(List<?> checks, String path) {
        for (int i = 0; i < checks.size(); i++) {
            validateSegmentReference(checks.get(i), path + "[" + i + "]");
        }
    }

    private void validateSegmentReference(Object raw, String path) {
        Map<String, Object> value = map(raw, path, "check");
        for (String group : Arrays.asList("and", "or", "not")) {
            if (value.get(group) instanceof List) {
                List<?> children = (List<?>) value.get(group);
                for (int i = 0; i < children.size(); i++) {
                    validateSegmentReference(children.get(i), path + "." + group + "[" + i + "]");
                }
                return;
            }
        }
        if (!(value.get("segment") instanceof String)) return;
        String segment = (String) value.get("segment");
        String error = segmentValidator.validate(segment);
        if (error != null) fail(path, error);
    }

    private void validateFeatureExpectation(Map<String, Object> value, String path) {
        Set<String> allowed = new HashSet<>(Arrays.asList("feature", "enabled", "variation", "key", "description"));
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) fail(path, "unknown property \"" + key + "\"");
        }
        if (!(value.get("feature") instanceof String) || ((String) value.get("feature")).isEmpty()) {
            fail(path, "feature must be a nonempty string");
        }
        if (value.containsKey("enabled") && !(value.get("enabled") instanceof Boolean)) {
            fail(path, "enabled must be a boolean");
        }
        if (value.containsKey("variation")
                && value.get("variation") != null
                && !(value.get("variation") instanceof String)) {
            fail(path, "variation must be a string or null");
        }
    }

    private void validateReference(Map<String, Object> reference, String path, boolean nested) {
        Set<String> allowed = nested
            ? new HashSet<>(Arrays.asList("variable", "feature", "path"))
            : new HashSet<>(Arrays.asList("variable", "feature", "path", "operator", "value", "compareTo", "regexFlags", "key", "description"));
        for (String key : reference.keySet()) if (!allowed.contains(key)) fail(path, "unknown reference property \"" + key + "\"");
        if (reference.containsKey("feature") && !reference.containsKey("variable")) {
            fail(path, "feature references require an explicit variable");
        }
        for (String key : Arrays.asList("variable", "feature")) {
            if (reference.containsKey(key) && (!(reference.get(key) instanceof String) || ((String) reference.get(key)).isEmpty())) {
                fail(path, key + " must be a nonempty string");
            }
        }
        if (reference.containsKey("path") && reference.get("path") == null) fail(path, "path cannot be null");
        parts(reference.get("path"), path);
    }

    private void validateLiteral(String operator, Object value, Object flags, String path) {
        if (operator.equals("equals") || operator.equals("notEquals")) {
            if (!primitive(value)) fail(path, operator + " requires a scalar value");
        } else if (Arrays.asList("greaterThan", "greaterThanOrEquals", "lessThan", "lessThanOrEquals").contains(operator)) {
            if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) fail(path, operator + " requires a finite numeric value");
        } else if (Arrays.asList("contains", "notContains", "startsWith", "endsWith").contains(operator)) {
            if (!(value instanceof String)) fail(path, operator + " requires a string value");
        } else if (operator.equals("in") || operator.equals("notIn") || operator.equals("includesAny") || operator.equals("notIncludesAny")) {
            if (!(value instanceof List)) fail(path, operator + " requires an array value");
            for (Object item : (List<?>) value) if (!primitive(item)) fail(path, operator + " requires an array of scalar values");
        } else if (operator.equals("includes") || operator.equals("notIncludes")) {
            if (!primitive(value)) fail(path, operator + " requires a scalar value");
        } else if (operator.equals("matches") || operator.equals("notMatches")) {
            if (!(value instanceof String)) fail(path, operator + " requires a string value");
            validateRegex((String) value, flags, path);
        } else if (operator.startsWith("semver")) {
            if (!(value instanceof String)) fail(path, operator + " requires a string value");
            semver(value, value, path);
        } else if (operator.equals("before") || operator.equals("after")) {
            if (!(value instanceof String)) fail(path, operator + " requires a portable timestamp");
            parseDate((String) value, path);
        }
    }

    private boolean node(Object raw, String path, List<Object> tracePath, List<Map<String, Object>> evaluated) {
        Map<String, Object> value = map(raw, path, "check");
        String displayPath = namedPath(path, value);
        List<String> groups = groups(value);
        if (groups.isEmpty()) return leaf(value, displayPath, tracePath, evaluated);
        String op = groups.get(0);
        List<?> children = (List<?>) value.get(op);
        if (op.equals("and")) {
            for (int i = 0; i < children.size(); i++) if (!node(children.get(i), path + ".and[" + i + "]", appendPath(tracePath, "and", i), evaluated)) return false;
            return true;
        }
        if (op.equals("or")) {
            String lastPath = null;
            String lastDetail = null;
            for (int i = 0; i < children.size(); i++) {
                if (node(children.get(i), path + ".or[" + i + "]", appendPath(tracePath, "or", i), evaluated)) return true;
                lastPath = failedPath;
                lastDetail = failedDetail;
            }
            failedPath = lastPath == null ? path : lastPath;
            failedDetail = lastDetail == null ? "no OR branch matched" : lastDetail;
            return false;
        }
        for (int i = 0; i < children.size(); i++) if (!node(children.get(i), path + ".not[" + i + "]", appendPath(tracePath, "not", i), evaluated)) return true;
        failedPath = path;
        failedDetail = "NOT group matched";
        return false;
    }

    private List<Object> appendPath(List<Object> path, Object... values) {
        List<Object> result = new ArrayList<>(path);
        result.addAll(Arrays.asList(values));
        return result;
    }

    private boolean leaf(Map<String, Object> leaf, String path, List<Object> tracePath, List<Map<String, Object>> evaluated) {
        if (leaf.containsKey("segment") || leaf.containsKey("matches")) {
            return segmentLeaf(leaf, path, tracePath, evaluated);
        }
        if (leaf.containsKey("enabled") || leaf.containsKey("variation")) {
            return featureLeaf(leaf, path, tracePath, evaluated);
        }
        String operator = (String) leaf.get("operator");
        Map<String, Object> leftReference = referenceFrom(leaf);
        Resolved left = resolve(leftReference, path, "left");
        Map<String, Object> rightReference = leaf.containsKey("compareTo")
            ? map(leaf.get("compareTo"), path, "compareTo") : null;
        Resolved right = rightReference == null
            ? new Resolved(true, leaf.get("value"), display(leaf.get("value")))
            : resolve(rightReference, path, "right");
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("path", tracePath);
        if (leaf.get("key") != null) trace.put("key", leaf.get("key"));
        if (leaf.get("description") != null) trace.put("description", leaf.get("description"));
        trace.put("reference", leftReference);
        trace.put("operator", operator);
        trace.put("expected", reportValue(right));
        trace.put("actual", reportValue(left));
        if (!left.present) trace.put("actualMissing", true);
        if (!right.present) trace.put("expectedMissing", true);
        if (rightReference != null) trace.put("compareTo", rightReference);
        evaluated.add(trace);
        boolean matched = compare(operator, left, right, leaf.get("regexFlags"), path);
        if (!matched) {
            failedPath = path;
            failedDetail = "left " + display(leftReference) + " resolved to " + left.display + "; "
                + (rightReference == null ? "value" : "right " + display(rightReference))
                + " resolved to " + right.display + "; operator " + operator;
        }
        return matched;
    }

    private boolean segmentLeaf(Map<String, Object> leaf, String path, List<Object> tracePath,
            List<Map<String, Object>> evaluated) {
        String segment = (String) leaf.get("segment");
        boolean expected = (Boolean) leaf.get("matches");
        Resolution resolution = segmentResolver.resolve(segment);
        if (resolution.error != null) fail(path, resolution.error);
        if (!(resolution.value instanceof Boolean)) fail(path, "segment \"" + segment + "\" did not return a boolean match");
        boolean actual = (Boolean) resolution.value;
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("path", tracePath);
        if (leaf.get("key") != null) trace.put("key", leaf.get("key"));
        if (leaf.get("description") != null) trace.put("description", leaf.get("description"));
        trace.put("reference", Collections.singletonMap("segment", segment));
        trace.put("property", "matches");
        trace.put("operator", "equals");
        trace.put("expected", expected);
        trace.put("actual", actual);
        evaluated.add(trace);
        if (actual != expected) {
            failedPath = path;
            failedDetail = "segment \"" + segment + "\" expected matches=" + expected + ", actual matches=" + actual;
        }
        return actual == expected;
    }

    private String namedPath(String path, Map<String, Object> value) {
        List<String> metadata = new ArrayList<>();
        if (value.get("key") instanceof String) metadata.add("key=" + display(value.get("key")));
        if (value.get("description") instanceof String) metadata.add("description=" + display(value.get("description")));
        return metadata.isEmpty() ? path : path + " [" + String.join(", ", metadata) + "]";
    }

    private boolean featureLeaf(Map<String, Object> leaf, String path, List<Object> tracePath,
            List<Map<String, Object>> evaluated) {
        String feature = (String) leaf.get("feature");
        boolean matched = true;
        List<String> details = new ArrayList<>();
        if (leaf.containsKey("enabled")) {
            boolean expected = (Boolean) leaf.get("enabled");
            Resolution actual = featureResolver.resolve(feature, "enabled");
            if (actual.error != null) {
                fail(path, "feature \"" + feature + "\" enabled getter failed: " + actual.error);
            }
            Map<String, Object> trace = featureTrace(leaf, tracePath, "enabled", expected, actual.value);
            evaluated.add(trace);
            matched = Objects.equals(expected, actual.value);
            details.add("expected enabled " + expected + ", actual " + actual.value);
        }
        if (leaf.containsKey("variation")) {
            String expected = (String) leaf.get("variation");
            Resolution actual = featureResolver.resolve(feature, "variation");
            if (actual.error != null) {
                fail(path, "feature \"" + feature + "\" variation getter failed: " + actual.error);
            }
            Map<String, Object> trace = featureTrace(leaf, tracePath, "variation", expected, actual.value);
            evaluated.add(trace);
            boolean variationMatched = Objects.equals(expected, actual.value);
            matched = matched && variationMatched;
            details.add("expected variation " + display(expected) + ", actual " + display(actual.value));
        }
        if (!matched) {
            failedPath = path;
            failedDetail = "feature \"" + feature + "\": " + String.join("; ", details);
        }
        return matched;
    }

    private Map<String, Object> featureTrace(Map<String, Object> leaf, List<Object> tracePath,
            String property, Object expected, Object actual) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("path", tracePath);
        if (leaf.get("key") != null) trace.put("key", leaf.get("key"));
        if (leaf.get("description") != null) trace.put("description", leaf.get("description"));
        trace.put("reference", Collections.singletonMap("feature", leaf.get("feature")));
        trace.put("property", property);
        trace.put("operator", "equals");
        trace.put("expected", expected);
        trace.put("actual", actual);
        return trace;
    }

    private Object reportValue(Resolved value) {
        if (value.present) return value.value;
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("$type", "undefined");
        return missing;
    }

    private Resolved resolve(Map<String, Object> reference, String path, String side) {
        Resolution resolution = resolver.resolve(reference);
        if (resolution.error != null) fail(path, side + " " + display(reference) + ": " + resolution.error);
        Object current = resolution.value;
        boolean present = true;
        for (Object part : parts(reference.get("path"), path)) {
            if (part instanceof Number) {
                long index = ((Number) part).longValue();
                if (!(current instanceof List) || index >= ((List<?>) current).size()) {
                    present = false; current = null; break;
                }
                current = ((List<?>) current).get((int) index);
            } else if (current instanceof List && "length".equals(part)) {
                current = ((List<?>) current).size();
            } else if (current instanceof List && ((String) part).matches("^(0|[1-9][0-9]*)$")) {
                try {
                    long index = Long.parseLong((String) part);
                    if (index >= ((List<?>) current).size()) { present = false; current = null; break; }
                    current = ((List<?>) current).get((int) index);
                } catch (NumberFormatException ignored) { present = false; current = null; break; }
            } else if (current instanceof Map && ((Map<?, ?>) current).containsKey(part)) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                present = false; current = null; break;
            }
        }
        return new Resolved(present, current, present ? display(current) : "<missing>");
    }

    private List<Object> parts(Object raw, String path) {
        List<?> source;
        boolean stringPath = raw instanceof String;
        if (raw == null) source = Collections.emptyList();
        else if (raw instanceof String) source = ((String) raw).isEmpty()
            ? Collections.emptyList() : Arrays.asList(((String) raw).split("\\.", -1));
        else if (raw instanceof List) source = (List<?>) raw;
        else { fail(path, "path must be a string or array"); return Collections.emptyList(); }
        List<Object> result = new ArrayList<>();
        for (Object value : source) {
            if (value instanceof Number && !stringPath) {
                double number = ((Number) value).doubleValue();
                if (number >= 0 && number == Math.rint(number) && number <= 9_007_199_254_740_991L) {
                    result.add(((Number) value).longValue()); continue;
                }
            }
            if (value instanceof String) {
                String text = (String) value;
                if (stringPath && text.matches("^(0|[1-9][0-9]*)$")) {
                    try {
                        long index = Long.parseLong(text);
                        if (index > 9_007_199_254_740_991L) fail(path, "path index must be a safe integer");
                        result.add(index); continue;
                    } catch (NumberFormatException ignored) { fail(path, "path index must be a safe integer"); }
                }
                if (!text.isEmpty() && !text.contains("*") && !UNSAFE.contains(text)) { result.add(text); continue; }
            }
            fail(path, "path contains an unsafe or invalid component");
        }
        return result;
    }

    private boolean compare(String operator, Resolved left, Resolved right, Object flags, String path) {
        if (operator.equals("exists")) return left.present;
        if (operator.equals("notExists")) return !left.present;
        if (operator.equals("isNull")) return left.present && left.value == null;
        if (operator.equals("notNull")) return left.present && left.value != null;
        if (operator.equals("empty") || operator.equals("notEmpty")) {
            if (!left.present || left.value == null) return false;
            boolean empty;
            if (left.value instanceof String) empty = ((String) left.value).isEmpty();
            else if (left.value instanceof List) empty = ((List<?>) left.value).isEmpty();
            else if (left.value instanceof Map) empty = ((Map<?, ?>) left.value).isEmpty();
            else {
                fail(path, operator + " requires a string, array, or object operand");
                return false;
            }
            return operator.equals("empty") ? empty : !empty;
        }
        if (!left.present || !right.present) return false;
        switch (operator) {
            case "equals": return equality(left.value, right.value, path);
            case "notEquals": return !equality(left.value, right.value, path);
            case "in": return membership(left.value, right.value, path);
            case "notIn": return !membership(left.value, right.value, path);
            case "contains": return strings(left.value, right.value, path, String::contains);
            case "notContains": return !strings(left.value, right.value, path, String::contains);
            case "startsWith": return strings(left.value, right.value, path, String::startsWith);
            case "endsWith": return strings(left.value, right.value, path, String::endsWith);
            case "greaterThan": return numbers(left.value, right.value, path, (a, b) -> a > b);
            case "greaterThanOrEquals": return numbers(left.value, right.value, path, (a, b) -> a >= b);
            case "lessThan": return numbers(left.value, right.value, path, (a, b) -> a < b);
            case "lessThanOrEquals": return numbers(left.value, right.value, path, (a, b) -> a <= b);
            case "includes": return includes(left.value, right.value, path);
            case "notIncludes": return !includes(left.value, right.value, path);
            case "includesAny": return includesAny(left.value, right.value, path);
            case "notIncludesAny": return !includesAny(left.value, right.value, path);
            case "matches": return matches(left.value, right.value, flags, path);
            case "notMatches": return !matches(left.value, right.value, flags, path);
            case "semverEquals": return semver(left.value, right.value, path) == 0;
            case "semverNotEquals": return semver(left.value, right.value, path) != 0;
            case "semverGreaterThan": return semver(left.value, right.value, path) > 0;
            case "semverGreaterThanOrEquals": return semver(left.value, right.value, path) >= 0;
            case "semverLessThan": return semver(left.value, right.value, path) < 0;
            case "semverLessThanOrEquals": return semver(left.value, right.value, path) <= 0;
            case "before": return date(left.value, right.value, path) < 0;
            case "after": return date(left.value, right.value, path) > 0;
            default: fail(path, "unknown operator \"" + operator + "\""); return false;
        }
    }

    private boolean equality(Object left, Object right, String path) {
        if (!primitive(left) || !primitive(right)) fail(path, "equals/notEquals requires scalar operands");
        return scalarEquals(left, right);
    }
    private boolean scalarEquals(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            double a = ((Number) left).doubleValue(), b = ((Number) right).doubleValue();
            return Double.isFinite(a) && Double.isFinite(b) && a == b;
        }
        return left == null ? right == null : left.getClass().equals(right == null ? null : right.getClass()) && left.equals(right);
    }
    private boolean membership(Object left, Object right, String path) {
        if (!(left == null || left instanceof String || left instanceof Number) || !(right instanceof List)) {
            fail(path, "in/notIn requires a string, number, or null left value and an array");
        }
        for (Object value : (List<?>) right) if (scalarEquals(left, value)) return true;
        return false;
    }
    private boolean includes(Object left, Object right, String path) {
        if (!(left instanceof List) || !primitive(right)) fail(path, "includes/notIncludes requires an array and a scalar");
        for (Object value : (List<?>) left) if (scalarEquals(value, right)) return true;
        return false;
    }
    private boolean includesAny(Object left, Object right, String path) {
        if (!(left instanceof List) || !(right instanceof List)) fail(path, "includesAny/notIncludesAny requires two arrays");
        for (Object candidate : (List<?>) right) {
            if (!primitive(candidate)) fail(path, "includesAny/notIncludesAny requires an array of primitive values");
        }
        for (Object candidate : (List<?>) right) {
            for (Object item : (List<?>) left) if (scalarEquals(item, candidate)) return true;
        }
        return false;
    }
    private boolean strings(Object left, Object right, String path, BiPredicate<String, String> test) {
        if (!(left instanceof String) || !(right instanceof String)) fail(path, "operator requires string operands");
        return test.test((String) left, (String) right);
    }
    private boolean numbers(Object left, Object right, String path, BiPredicate<Double, Double> test) {
        if (!(left instanceof Number) || !(right instanceof Number)) fail(path, "operator requires finite numeric operands");
        double a = ((Number) left).doubleValue(), b = ((Number) right).doubleValue();
        if (!Double.isFinite(a) || !Double.isFinite(b)) fail(path, "operator requires finite numeric operands");
        return test.test(a, b);
    }
    private boolean matches(Object left, Object right, Object rawFlags, String path) {
        if (!(left instanceof String) || !(right instanceof String)) fail(path, "matches/notMatches requires string operands");
        String expression = (String) right;
        String flags = regexFlags(rawFlags, path);
        int options = 0;
        if (flags.contains("i")) options |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (flags.contains("m")) options |= Pattern.MULTILINE;
        if (flags.contains("s")) options |= Pattern.DOTALL;
        validateRegex(expression, rawFlags, path);
        try { return Pattern.compile(expression, options).matcher((String) left).find(); }
        catch (PatternSyntaxException error) { fail(path, "invalid regular expression: " + error.getDescription()); return false; }
    }
    private void validateRegex(String expression, Object rawFlags, String path) {
        String flags = regexFlags(rawFlags, path);
        if (expression.contains("(?")) fail(path, "unsupported portable regular expression");
        if (Pattern.compile("\\\\(?:[1-9]|k<|k'|g<|g')").matcher(expression).find()) fail(path, "unsupported portable regular expression");
        if (Pattern.compile("(?:[?*+]|\\{\\d+(?:,\\d*)?\\})\\+").matcher(expression).find()) fail(path, "unsupported portable regular expression");
        int options = 0;
        if (flags.contains("i")) options |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (flags.contains("m")) options |= Pattern.MULTILINE;
        if (flags.contains("s")) options |= Pattern.DOTALL;
        try { Pattern.compile(expression, options); }
        catch (PatternSyntaxException error) { fail(path, "invalid regular expression: " + error.getDescription()); }
    }
    private String regexFlags(Object raw, String path) {
        if (raw != null && !(raw instanceof String)) fail(path, "regexFlags must be a string");
        String flags = raw == null ? "" : (String) raw;
        Set<Character> seen = new HashSet<>();
        for (char value : flags.toCharArray()) if ("gims".indexOf(value) < 0 || !seen.add(value)) fail(path, "invalid portable regex flags \"" + flags + "\"");
        return flags;
    }
    private int semver(Object left, Object right, String path) {
        if (!(left instanceof String) || !(right instanceof String)) fail(path, "semver operator requires string operands");
        try {
            return CompareVersions.compareVersions((String) left, (String) right);
        } catch (RuntimeException error) {
            throw new CheckException(path, error.getMessage());
        }
    }
    private int date(Object left, Object right, String path) {
        if (!(left instanceof String) || !(right instanceof String)) fail(path, "date operator requires portable timestamps");
        return parseDate((String) left, path).compareTo(parseDate((String) right, path));
    }
    private Instant parseDate(String value, String path) {
        java.util.regex.Matcher match = DATE.matcher(value);
        if (!match.matches()) fail(path, "date operator requires a portable timestamp");
        int year = Integer.parseInt(match.group(1));
        if (year == 0) fail(path, "date operator requires a valid Gregorian timestamp");
        try {
            int nanos = match.group(7) == null ? 0
                : Integer.parseInt((match.group(7) + "00").substring(0, 3)) * 1_000_000;
            LocalDateTime local = LocalDateTime.of(
                year,
                Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)),
                Integer.parseInt(match.group(4)),
                Integer.parseInt(match.group(5)),
                Integer.parseInt(match.group(6)),
                nanos
            );
            int offsetSeconds = 0;
            if (!"Z".equals(match.group(8))) {
                int offsetHour = Integer.parseInt(match.group(10));
                int offsetMinute = Integer.parseInt(match.group(11));
                if (offsetHour > 14 || offsetMinute > 59 || (offsetHour == 14 && offsetMinute != 0)) {
                    fail(path, "date operator requires an offset up to 14:00");
                }
                offsetSeconds = (offsetHour * 60 + offsetMinute) * 60;
                if ("-".equals(match.group(9))) offsetSeconds = -offsetSeconds;
            }
            Instant instant = local.toInstant(ZoneOffset.ofTotalSeconds(offsetSeconds));
            int utcYear = instant.atOffset(ZoneOffset.UTC).getYear();
            if (utcYear < 1 || utcYear > 9999) fail(path, "date UTC instant is outside years 0001 to 9999");
            return instant;
        } catch (DateTimeException | NumberFormatException error) {
            fail(path, "date operator requires a valid Gregorian timestamp");
            return Instant.EPOCH;
        }
    }

    private boolean primitive(Object value) { return value == null || value instanceof String || value instanceof Number || value instanceof Boolean; }
    private List<String> groups(Map<String, Object> value) {
        List<String> result = new ArrayList<>();
        for (String op : Arrays.asList("and", "or", "not")) if (value.containsKey(op)) result.add(op);
        return result;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value, String path, String label) {
        if (!(value instanceof Map)) { fail(path, label + " must be an object"); return Collections.emptyMap(); }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }
    private Map<String, Object> referenceFrom(Map<String, Object> leaf) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : Arrays.asList("variable", "feature", "path")) if (leaf.containsKey(key)) result.put(key, leaf.get(key));
        return result;
    }
    private String display(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception ignored) { return String.valueOf(value); } }
    private void fail(String path, String message) { throw new CheckException(path, message); }

    private static final class Resolved {
        final boolean present; final Object value; final String display;
        Resolved(boolean present, Object value, String display) { this.present = present; this.value = value; this.display = display; }
    }
    private static final class CheckException extends RuntimeException {
        final String path;
        CheckException(String path, String message) { super(message); this.path = path; }
    }
}
