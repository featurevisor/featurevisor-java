package com.featurevisor.sdk;

import com.featurevisor.sdk.Allocation;
import com.featurevisor.sdk.Condition;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.Force;
import com.featurevisor.sdk.Operator;
import com.featurevisor.sdk.Range;
import com.featurevisor.sdk.Segment;
import com.featurevisor.sdk.Traffic;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InstanceEvaluationDataProvider for Featurevisor SDK
 * Handles reading and parsing datafile content
 */
class InstanceEvaluationDataProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Options for creating a InstanceEvaluationDataProvider
     */
    public static class InstanceEvaluationDataProviderOptions {
        private DatafileContent datafile;
        private DiagnosticReporter diagnostics;

        public InstanceEvaluationDataProviderOptions() {}

        public InstanceEvaluationDataProviderOptions datafile(DatafileContent datafile) {
            this.datafile = datafile;
            return this;
        }

        public InstanceEvaluationDataProviderOptions diagnostics(DiagnosticReporter diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        // Getters
        public DatafileContent getDatafile() { return datafile; }
        public DiagnosticReporter getDiagnostics() { return diagnostics; }
    }

    /**
     * Result of force matching
     */
    public static class ForceResult {
        private Force force;
        private Integer forceIndex;

        public ForceResult() {}

        public ForceResult(Force force, Integer forceIndex) {
            this.force = force;
            this.forceIndex = forceIndex;
        }

        // Getters and setters
        public Force getForce() { return force; }
        public void setForce(Force force) { this.force = force; }
        public Integer getForceIndex() { return forceIndex; }
        public void setForceIndex(Integer forceIndex) { this.forceIndex = forceIndex; }
    }

    private String schemaVersion;
    private String revision;
    private String featurevisorVersion;
    private Map<String, Segment> segments;
    private Map<String, Feature> features;
    private Map<String, GlobalVariable> variables;
    private DiagnosticReporter diagnostics;
    private Dependencies.Index dependencyIndex;

    synchronized Dependencies.Index getDependencyIndex() {
        if (dependencyIndex == null) dependencyIndex = Dependencies.create(getDatafile());
        return dependencyIndex;
    }

    synchronized Dependencies.Index deriveDependencyIndex(InstanceEvaluationDataProvider previous) {
        if (dependencyIndex == null) dependencyIndex = Dependencies.update(
            previous.getDependencyIndex(), previous.getDatafile(), getDatafile());
        return dependencyIndex;
    }

    synchronized int getCachedDependencyIndexCount() { return dependencyIndex == null ? 0 : 1; }

    // Cache for regex patterns to avoid creating new objects for the same regex
    private Map<String, Pattern> regexCache;
    private Map<String, Object> parsedExpressionCache;
    private static final class EntityCache<T> {
        private T value;
        T get(java.util.function.Supplier<Normalize.Result<T>> normalize, InstanceEvaluationDataProvider owner) {
            Normalize.Result<T> result;
            synchronized (this) {
                if (value != null) return value;
                result = normalize.get();
                value = result.value;
            }
            owner.reportErrors(result.errors);
            return result.value;
        }
    }
    private final Map<String, EntityCache<Feature>> normalizedFeatures = new ConcurrentHashMap<>();
    private final Map<String, EntityCache<Segment>> normalizedSegments = new ConcurrentHashMap<>();
    private final Map<String, EntityCache<GlobalVariable>> normalizedVariables = new ConcurrentHashMap<>();

    public InstanceEvaluationDataProvider(InstanceEvaluationDataProviderOptions options) {
        DatafileContent datafile = options.getDatafile();
        this.diagnostics = options.getDiagnostics();

        this.schemaVersion = datafile.getSchemaVersion();
        this.revision = datafile.getRevision();
        this.featurevisorVersion = datafile.getFeaturevisorVersion();
        this.segments = datafile.getSegments();
        this.features = datafile.getFeatures();
        this.variables = datafile.getVariables();
        if (this.segments == null) {
            this.segments = new HashMap<>();
        }
        if (this.features == null) {
            this.features = new HashMap<>();
        }
        if (this.variables == null) { this.variables = new HashMap<>(); }
        features.keySet().forEach(key -> normalizedFeatures.put(key, new EntityCache<>()));
        segments.keySet().forEach(key -> normalizedSegments.put(key, new EntityCache<>()));
        variables.keySet().forEach(key -> normalizedVariables.put(key, new EntityCache<>()));
        this.regexCache = new ConcurrentHashMap<>();
        this.parsedExpressionCache = new ConcurrentHashMap<>();
    }

    public String getRevision() {
        return revision;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public DatafileContent getDatafile() {
        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion(this.schemaVersion);
        datafile.setRevision(this.revision);
        datafile.setFeaturevisorVersion(this.featurevisorVersion);
        datafile.setSegments(this.segments);
        datafile.setFeatures(this.features);
        datafile.setVariables(this.variables);
        return datafile;
    }

    public Segment getSegment(String segmentKey) {
        Segment segment = segments.get(segmentKey);

        if (segment == null) {
            return null;
        }

        return normalizedSegments.get(segmentKey).get(() -> Normalize.segment(segmentKey, segment, false), this);
    }

    public List<String> getFeatureKeys() {
        return new ArrayList<>(features.keySet());
    }

    public Feature getFeature(String featureKey) {
        Feature source = features.get(featureKey);
        return source == null ? null : normalizedFeatures.get(featureKey).get(() -> Normalize.feature(featureKey, source, false), this);
    }

    public List<String> getGlobalVariableKeys() { return new ArrayList<>(variables.keySet()); }
    public GlobalVariable getGlobalVariable(String key) {
        GlobalVariable source = variables.get(key);
        return source == null ? null : normalizedVariables.get(key).get(() -> Normalize.variable(key, source, false), this);
    }

    void reportErrors(List<Map<String, Object>> errors) {
        for (Map<String, Object> error : errors) {
            Map<String, Object> details = new LinkedHashMap<>(error);
            details.put("code", "conditions_parse_error");
            diagnostics.error("Error parsing conditions", details);
        }
    }

    void retainCaches(InstanceEvaluationDataProvider previous, DatafileContent incoming) {
        previous.normalizedFeatures.forEach((key, value) -> {
            if (!incoming.getFeatures().containsKey(key) && features.get(key) == previous.features.get(key)) normalizedFeatures.put(key, value);
        });
        previous.normalizedSegments.forEach((key, value) -> {
            if (!incoming.getSegments().containsKey(key) && segments.get(key) == previous.segments.get(key)) normalizedSegments.put(key, value);
        });
        previous.normalizedVariables.forEach((key, value) -> {
            if ((incoming.getVariables() == null || !incoming.getVariables().containsKey(key)) && variables.get(key) == previous.variables.get(key)) normalizedVariables.put(key, value);
        });
    }

    public List<String> getVariableKeys(String featureKey) {
        Feature feature = getFeature(featureKey);

        if (feature == null || feature.getVariablesSchema() == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(feature.getVariablesSchema().keySet());
    }

    public boolean hasVariations(String featureKey) {
        Feature feature = getFeature(featureKey);

        if (feature == null) {
            return false;
        }

        return feature.getVariations() != null && !feature.getVariations().isEmpty();
    }

    public Pattern getRegex(String regexString, String regexFlags) {
        String flags = regexFlags != null ? regexFlags : "";
        String cacheKey = regexString + "-" + flags;

        if (regexCache.containsKey(cacheKey)) {
            return regexCache.get(cacheKey);
        }

        try {
            Pattern regex = Pattern.compile(Conditions.normalizePortableRegex(regexString), getPatternFlags(flags));
            regexCache.put(cacheKey, regex);
            return regex;
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    private int getPatternFlags(String flags) {
        int patternFlags = 0;
        if (flags.contains("i")) patternFlags |= Pattern.CASE_INSENSITIVE;
        if (flags.contains("m")) patternFlags |= Pattern.MULTILINE;
        if (flags.contains("s")) patternFlags |= Pattern.DOTALL;
        if (flags.contains("u")) patternFlags |= Pattern.UNICODE_CASE;
        for (char flag : flags.toCharArray()) {
            if ("gimsuy".indexOf(flag) == -1) {
                throw new IllegalArgumentException("Invalid regular expression flag: " + flag);
            }
        }
        return patternFlags;
    }

    public boolean allConditionsAreMatched(Object conditions, Map<String, Object> context) {
        if (conditions instanceof String) {
            String conditionsStr = (String) conditions;
            if ("*".equals(conditionsStr)) {
                return true;
            }
            return false;
        }

        Conditions.GetRegex getRegex = (regexString, regexFlags) -> this.getRegex(regexString, regexFlags);

        // Handle Condition object
        if (conditions instanceof Condition) {
            Condition condition = (Condition) conditions;
            try {
                return Conditions.conditionIsMatched(condition, context, getRegex);
            } catch (Exception e) {
                Map<String, Object> details = new HashMap<>();
                details.put("code", "condition_match_error");
                details.put("originalError", e);
                details.put("condition", condition);
                details.put("context", context);
                diagnostics.warn(e.getMessage(), details);
                return false;
            }
        }

        // Handle Map (plain condition object)
        if (conditions instanceof Map) {
            Map<String, Object> conditionMap = (Map<String, Object>) conditions;

            // Check if it's a plain condition with attribute
            if (conditionMap.containsKey("attribute")) {
                try {
                    // Convert Map to Condition object
                    Condition condition = new Condition();
                    condition.setAttribute((String) conditionMap.get("attribute"));

                    String operatorStr = (String) conditionMap.get("operator");
                    if (operatorStr != null) {
                        for (Operator op : Operator.values()) {
                            if (op.getValue().equals(operatorStr)) {
                                condition.setOperator(op);
                                break;
                            }
                        }
                    }

                    condition.setValue(conditionMap.get("value"));
                    condition.setRegexFlags((String) conditionMap.get("regexFlags"));

                    return Conditions.conditionIsMatched(condition, context, getRegex);
                } catch (Exception e) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("code", "condition_match_error");
                    details.put("originalError", e);
                    details.put("condition", copyDiagnosticCondition(conditionMap));
                    details.put("context", context);
                    diagnostics.warn(e.getMessage(), details);
                    return false;
                }
            }
        }

        // Handle Map (complex conditions)
        if (conditions instanceof Map) {
            Map<String, Object> conditionsMap = (Map<String, Object>) conditions;

            // Handle AND conditions
            if (conditionsMap.containsKey("and") && conditionsMap.get("and") instanceof List) {
                List<Object> andConditions = (List<Object>) conditionsMap.get("and");
                return andConditions.stream().allMatch(c -> allConditionsAreMatched(c, context));
            }

            // Handle OR conditions
            if (conditionsMap.containsKey("or") && conditionsMap.get("or") instanceof List) {
                List<Object> orConditions = (List<Object>) conditionsMap.get("or");
                return orConditions.stream().anyMatch(c -> allConditionsAreMatched(c, context));
            }

            // Handle NOT conditions
            if (conditionsMap.containsKey("not") && conditionsMap.get("not") instanceof List) {
                List<Object> notConditions = (List<Object>) conditionsMap.get("not");
                Map<String, Object> andCondition = new HashMap<>();
                andCondition.put("and", notConditions);
                return !allConditionsAreMatched(andCondition, context);
            }
        }

        // Handle List of conditions
        if (conditions instanceof List) {
            List<Object> conditionsList = (List<Object>) conditions;

            // If the list contains only one item and it's a Map with and/or/not, treat it as a single complex condition
            if (conditionsList.size() == 1 && conditionsList.get(0) instanceof Map) {
                Map<String, Object> singleCondition = (Map<String, Object>) conditionsList.get(0);
                if (singleCondition.containsKey("and") || singleCondition.containsKey("or") || singleCondition.containsKey("not")) {
                    return allConditionsAreMatched(singleCondition, context);
                }
            }

            // Otherwise, treat as a list of conditions (all must be true)
            return conditionsList.stream().allMatch(c -> allConditionsAreMatched(c, context));
        }

        return false;
    }

    private static Object copyDiagnosticCondition(Object value) {
        // Diagnostic callbacks must not mutate cached expression trees.
        if (value instanceof Map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> copy.put(key, copyDiagnosticCondition(item)));
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>(((List<?>) value).size());
            for (Object item : (List<?>) value) {
                copy.add(copyDiagnosticCondition(item));
            }
            return copy;
        }
        return value;
    }

    public boolean segmentIsMatched(Segment segment, Map<String, Object> context) {
        return allConditionsAreMatched(segment.getConditions(), context);
    }

    public boolean allSegmentsAreMatched(Object groupSegments, Map<String, Object> context) {
        if ("*".equals(groupSegments)) {
            return true;
        }

        if (groupSegments instanceof String) {
            String segmentKey = (String) groupSegments;
            Segment segment = getSegment(segmentKey);

            if (segment != null) {
                return segmentIsMatched(segment, context);
            }

            return false;
        }

        if (groupSegments instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> groupSegmentsMap = (Map<String, Object>) groupSegments;

            if (groupSegmentsMap.containsKey("and") && groupSegmentsMap.get("and") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> andSegments = (List<Object>) groupSegmentsMap.get("and");
                return andSegments.stream().allMatch(s -> allSegmentsAreMatched(s, context));
            }

            if (groupSegmentsMap.containsKey("or") && groupSegmentsMap.get("or") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> orSegments = (List<Object>) groupSegmentsMap.get("or");
                return orSegments.stream().anyMatch(s -> allSegmentsAreMatched(s, context));
            }

            if (groupSegmentsMap.containsKey("not") && groupSegmentsMap.get("not") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> notSegments = (List<Object>) groupSegmentsMap.get("not");
                return !notSegments.stream().allMatch(s -> allSegmentsAreMatched(s, context));
            }
        }

        if (groupSegments instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> groupSegmentsList = (List<Object>) groupSegments;
            return groupSegmentsList.stream().allMatch(s -> allSegmentsAreMatched(s, context));
        }

        return false;
    }

    public Traffic getMatchedTraffic(List<Traffic> traffic, Map<String, Object> context) {


        return traffic.stream()
            .filter(t -> allSegmentsAreMatched(parseSegmentsIfStringified(t.getSegments()), context))
            .findFirst()
            .orElse(null);
    }

    public Traffic getMatchedTraffic(List<Traffic> traffic, EvaluateOptions options) {
        if (traffic != null) for (Traffic entry : traffic) {
            if (Evaluate.conditionsAreMatched(entry.getConditions(), options)) return entry;
        }
        return null;
    }

    public ForceResult getMatchedForce(Feature feature, EvaluateOptions options) {
        if (feature.getForce() != null) for (int index = 0; index < feature.getForce().size(); index++) {
            Force force = feature.getForce().get(index);
            if (Evaluate.conditionsAreMatched(force.getConditions(), options)) return new ForceResult(force, index);
        }
        return new ForceResult();
    }

    public Allocation getMatchedAllocation(Traffic traffic, int bucketValue) {
        if (traffic.getAllocation() == null) {
            return null;
        }

        for (Allocation allocation : traffic.getAllocation()) {
            Range range = allocation.getRange();
            if (range != null && range.getRange() != null && range.getRange().size() >= 2) {
                int start = range.getRange().get(0);
                int end = range.getRange().get(1);

                if (start <= bucketValue && end >= bucketValue) {
                    return allocation;
                }
            }
        }

        return null;
    }

    public ForceResult getMatchedForce(Object featureKey, Map<String, Object> context) {
        ForceResult result = new ForceResult();

        Feature feature;
        if (featureKey instanceof String) {
            feature = getFeature((String) featureKey);
        } else if (featureKey instanceof Feature) {
            feature = (Feature) featureKey;
        } else {
            return result;
        }

        if (feature == null || feature.getForce() == null) {
            return result;
        }

        for (int i = 0; i < feature.getForce().size(); i++) {
            Force currentForce = feature.getForce().get(i);

            if (currentForce.getConditions() != null &&
                allConditionsAreMatched(parseConditionsIfStringified(currentForce.getConditions()), context)) {
                result.setForce(currentForce);
                result.setForceIndex(i);
                break;
            }

            if (currentForce.getSegments() != null &&
                allSegmentsAreMatched(parseSegmentsIfStringified(currentForce.getSegments()), context)) {
                result.setForce(currentForce);
                result.setForceIndex(i);
                break;
            }
        }

        return result;
    }

    public Object parseConditionsIfStringified(Object conditions) {
        if (!(conditions instanceof String)) {
            // already parsed
            return conditions;
        }

        String conditionsStr = (String) conditions;
        if ("*".equals(conditionsStr)) {
            // everyone
            return conditions;
        }

        Object cached = parsedExpressionCache.get(conditionsStr);
        if (cached != null) {
            return cached;
        }

        Object parsed = parseConditionsUncached(conditionsStr);
        if (parsed != null && parsed != conditions) {
            Object existing = parsedExpressionCache.putIfAbsent(conditionsStr, parsed);
            return existing != null ? existing : parsed;
        }
        return parsed;
    }

    private Object parseConditionsUncached(String conditions) {
        try {
            return OBJECT_MAPPER.readValue(conditions, Object.class);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("code", "conditions_parse_error");
            details.put("originalError", e);
            details.put("conditions", conditions);
            diagnostics.error("Error parsing conditions", details);
            return conditions;
        }
    }

    public Object parseSegmentsIfStringified(Object segments) {
        if (segments instanceof String) {
            String segmentsStr = (String) segments;
            if (segmentsStr.startsWith("{") || segmentsStr.startsWith("[")) {
                Object cached = parsedExpressionCache.get(segmentsStr);
                if (cached != null) {
                    return cached;
                }
                try {
                    Object parsed = OBJECT_MAPPER.readValue(segmentsStr, Object.class);
                    if (parsed == null) {
                        return null;
                    }
                    Object existing = parsedExpressionCache.putIfAbsent(segmentsStr, parsed);
                    return existing != null ? existing : parsed;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Error parsing segments", e);
                }
            }
        }

        return segments;
    }

    synchronized void clearCaches() {
        dependencyIndex = null;
        regexCache.clear();
        parsedExpressionCache.clear();
    }
}
