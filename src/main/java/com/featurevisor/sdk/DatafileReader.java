package com.featurevisor.sdk;

import com.featurevisor.types.Allocation;
import com.featurevisor.types.Condition;
import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Force;
import com.featurevisor.types.Operator;
import com.featurevisor.types.Range;
import com.featurevisor.types.Segment;
import com.featurevisor.types.Traffic;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * DatafileReader for Featurevisor SDK
 * Handles reading and parsing datafile content
 */
public class DatafileReader {

    /**
     * Functional interface for getting regex patterns
     */
    @FunctionalInterface
    public interface GetRegex {
        Pattern getRegex(String regexString, String regexFlags);
    }

    /**
     * Options for creating a DatafileReader
     */
    public static class DatafileReaderOptions {
        private DatafileContent datafile;
        private Logger logger;

        public DatafileReaderOptions() {}

        public DatafileReaderOptions datafile(DatafileContent datafile) {
            this.datafile = datafile;
            return this;
        }

        public DatafileReaderOptions logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        // Getters
        public DatafileContent getDatafile() { return datafile; }
        public Logger getLogger() { return logger; }
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
    private Map<String, Segment> segments;
    private Map<String, Feature> features;
    private Logger logger;

    // Cache for regex patterns to avoid creating new objects for the same regex
    private Map<String, Pattern> regexCache;

    public DatafileReader(DatafileReaderOptions options) {
        DatafileContent datafile = options.getDatafile();
        this.logger = options.getLogger();

        this.schemaVersion = datafile.getSchemaVersion();
        this.revision = datafile.getRevision();
        this.segments = datafile.getSegments();
        this.features = datafile.getFeatures();
        this.regexCache = new HashMap<>();
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
        datafile.setSegments(this.segments);
        datafile.setFeatures(this.features);
        return datafile;
    }

    public Segment getSegment(String segmentKey) {
        Segment segment = segments.get(segmentKey);

        if (segment == null) {
            return null;
        }

        segment.setConditions(parseConditionsIfStringified(segment.getConditions()));

        return segment;
    }

    public List<String> getFeatureKeys() {
        return new ArrayList<>(features.keySet());
    }

    public Feature getFeature(String featureKey) {
        return features.get(featureKey);
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
            Pattern regex = Pattern.compile(regexString, getPatternFlags(flags));
            regexCache.put(cacheKey, regex);
            return regex;
        } catch (PatternSyntaxException e) {
            logger.error("Invalid regex pattern: " + regexString, null);
            return null;
        }
    }

    private int getPatternFlags(String flags) {
        int patternFlags = 0;
        if (flags.contains("i")) patternFlags |= Pattern.CASE_INSENSITIVE;
        if (flags.contains("m")) patternFlags |= Pattern.MULTILINE;
        if (flags.contains("s")) patternFlags |= Pattern.DOTALL;
        if (flags.contains("u")) patternFlags |= Pattern.UNICODE_CASE;
        if (flags.contains("x")) patternFlags |= Pattern.COMMENTS;
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

        GetRegex getRegex = (regexString, regexFlags) -> this.getRegex(regexString, regexFlags);

        // Handle Condition object
        if (conditions instanceof Condition) {
            Condition condition = (Condition) conditions;
            try {
                return Conditions.conditionIsMatched(condition, context, getRegex);
            } catch (Exception e) {
                Map<String, Object> details = new HashMap<>();
                details.put("error", e);
                details.put("condition", condition);
                details.put("context", context);
                logger.warn(e.getMessage(), details);
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
                    details.put("error", e);
                    details.put("condition", conditionMap);
                    details.put("context", context);
                    logger.warn(e.getMessage(), details);
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
                // NOT conditions are true if ALL conditions are false
                // This matches the TypeScript implementation: conditions.not.every(() => allConditionsAreMatched({and: conditions.not}, context) === false)
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
                // This matches the TypeScript implementation: groupSegments.not.every((groupSegment) => allSegmentsAreMatched(groupSegment, context) === false)
                return notSegments.stream().allMatch(s -> !allSegmentsAreMatched(s, context));
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(conditionsStr, Object.class);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("error", e);
            details.put("conditions", conditions);
            logger.error("Error parsing conditions", details);
            return conditions;
        }
    }

    public Object parseSegmentsIfStringified(Object segments) {
        if (segments instanceof String) {
            String segmentsStr = (String) segments;
            if (segmentsStr.startsWith("{") || segmentsStr.startsWith("[")) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(segmentsStr, Object.class);
                } catch (Exception e) {
                    logger.error("Error parsing segments: " + segmentsStr, null);
                    return segments;
                }
            }
        }

        return segments;
    }
}
