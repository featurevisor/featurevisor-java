package com.featurevisor.sdk;

import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.DatafileContent;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Event parameter utilities for Featurevisor SDK
 * Provides methods to generate event details for various SDK events
 */
final class Events {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String fingerprint(Object value, String hash) {
        if (hash != null) return hash;
        try { return OBJECT_MAPPER.writeValueAsString(value); }
        catch (Exception ignored) { return String.valueOf(value); }
    }

    private static void collectSegmentKeys(Object value, Set<String> result) {
        if (value == null) return;
        if (value instanceof String) {
            String string = (String) value;
            if ("*".equals(string)) return;
            if (string.startsWith("{") || string.startsWith("[")) {
                try { collectSegmentKeys(OBJECT_MAPPER.readValue(string, Object.class), result); return; }
                catch (Exception ignored) { }
            }
            result.add(string);
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) collectSegmentKeys(item, result);
        } else if (value instanceof Map) {
            for (Object item : ((Map<?, ?>) value).values()) collectSegmentKeys(item, result);
        }
    }

    private static void collectRequiredFeatureKeys(List<Object> requirements, Set<String> result) {
        if (requirements == null) return;
        for (Object item : requirements) {
            if (item instanceof String) result.add((String) item);
            else if (item instanceof Map) {
                Object key = ((Map<?, ?>) item).get("feature");
                if (key == null) key = ((Map<?, ?>) item).get("key");
                if (key instanceof String) result.add((String) key);
            }
        }
    }

    private static void collectOverrides(Map<String, List<VariableOverride>> groups, Set<String> segments, Set<String> features) {
        if (groups == null) return;
        for (List<VariableOverride> overrides : groups.values()) {
            if (overrides == null) continue;
            for (VariableOverride override : overrides) {
                collectSegmentKeys(override.getSegments(), segments);
                collectRequiredFeatureKeys(override.getRequiredFeatures(), features);
            }
        }
    }

    private static List<Set<String>> featureDependencies(Feature feature) {
        Set<String> segments = new HashSet<>(), features = new HashSet<>();
        List<Object> required = feature.getRequiredFeatures() != null ? feature.getRequiredFeatures() : feature.getRequired();
        collectRequiredFeatureKeys(required, features);
        if (feature.getTraffic() != null) for (Traffic traffic : feature.getTraffic()) {
            collectSegmentKeys(traffic.getSegments(), segments);
            collectOverrides(traffic.getVariableOverrides(), segments, features);
        }
        if (feature.getForce() != null) for (Force force : feature.getForce()) collectSegmentKeys(force.getSegments(), segments);
        if (feature.getVariations() != null) for (Variation variation : feature.getVariations()) {
            collectOverrides(variation.getVariableOverrides(), segments, features);
        }
        return Arrays.asList(segments, features);
    }

    private static List<Set<String>> variableDependencies(GlobalVariable variable) {
        Set<String> segments = new HashSet<>(), features = new HashSet<>();
        collectRequiredFeatureKeys(variable.getRequiredFeatures(), features);
        if (variable.getOverrides() != null) for (VariableOverride override : variable.getOverrides()) {
            collectSegmentKeys(override.getSegments(), segments);
            collectRequiredFeatureKeys(override.getRequiredFeatures(), features);
        }
        return Arrays.asList(segments, features);
    }

    /**
     * Get parameters for sticky set event
     * @param previousStickyFeatures Previous sticky features map
     * @param newStickyFeatures New sticky features map
     * @param replace Whether the sticky features were replaced
     * @return Event details for sticky set event
     */
    public static FeaturevisorEventDetails getParamsForStickySetEvent(
            Map<String, Object> previousStickyFeatures,
            Map<String, Object> newStickyFeatures,
            boolean replace) {

        if (previousStickyFeatures == null) {
            previousStickyFeatures = new java.util.HashMap<>();
        }
        if (newStickyFeatures == null) {
            newStickyFeatures = new java.util.HashMap<>();
        }

        List<String> keysBefore = new ArrayList<>(previousStickyFeatures.keySet());
        List<String> keysAfter = new ArrayList<>(newStickyFeatures.keySet());

        // Combine all keys and get unique features affected
        List<String> allKeys = new ArrayList<>();
        allKeys.addAll(keysBefore);
        allKeys.addAll(keysAfter);

        List<String> uniqueFeaturesAffected = allKeys.stream()
                .distinct()
                .collect(Collectors.toList());

        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("features", uniqueFeaturesAffected);
        details.put("replaced", replace);

        return details;
    }

    public static FeaturevisorEventDetails getParamsForStickyVariablesSetEvent(
            Map<String, Object> previous, Map<String, Object> current, boolean replace) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (previous != null) keys.addAll(previous.keySet());
        if (current != null) keys.addAll(current.keySet());
        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("variables", new ArrayList<>(keys));
        details.put("replaced", replace);
        return details;
    }

    /**
     * Get parameters for datafile set event
     * @param previousDatafileContent Previous datafile content
     * @param newDatafileContent New datafile content
     * @return Event details for datafile set event
     */
    public static FeaturevisorEventDetails getParamsForDatafileSetEvent(
            DatafileContent previousDatafileContent,
            DatafileContent newDatafileContent,
            boolean replace) {

        if (previousDatafileContent == null) {
            previousDatafileContent = new DatafileContent();
        }
        if (newDatafileContent == null) {
            newDatafileContent = new DatafileContent();
        }

        String previousRevision = previousDatafileContent.getRevision();
        List<String> previousFeatureKeys = new ArrayList<>();
        if (previousDatafileContent.getFeatures() != null) {
            previousFeatureKeys = new ArrayList<>(previousDatafileContent.getFeatures().keySet());
        }

        String newRevision = newDatafileContent.getRevision();
        List<String> newFeatureKeys = new ArrayList<>();
        if (newDatafileContent.getFeatures() != null) {
            newFeatureKeys = new ArrayList<>(newDatafileContent.getFeatures().keySet());
        }

        Map<String, Feature> previousFeatures = previousDatafileContent.getFeatures() != null ? previousDatafileContent.getFeatures() : java.util.Collections.emptyMap();
        Map<String, Feature> newFeatures = newDatafileContent.getFeatures() != null ? newDatafileContent.getFeatures() : java.util.Collections.emptyMap();
        Set<String> changedFeatures = new HashSet<>();
        Set<String> allFeatureKeys = new HashSet<>(previousFeatureKeys); allFeatureKeys.addAll(newFeatureKeys);
        for (String key : allFeatureKeys) {
            Feature before = previousFeatures.get(key), after = newFeatures.get(key);
            if (before == null || after == null || !Objects.equals(fingerprint(before, before.getHash()), fingerprint(after, after.getHash()))) changedFeatures.add(key);
        }

        Map<String, Segment> previousSegments = previousDatafileContent.getSegments() != null ? previousDatafileContent.getSegments() : java.util.Collections.emptyMap();
        Map<String, Segment> newSegments = newDatafileContent.getSegments() != null ? newDatafileContent.getSegments() : java.util.Collections.emptyMap();
        Set<String> changedSegments = new HashSet<>();
        Set<String> allSegmentKeys = new HashSet<>(previousSegments.keySet()); allSegmentKeys.addAll(newSegments.keySet());
        for (String key : allSegmentKeys) if (!Objects.equals(fingerprint(previousSegments.get(key), null), fingerprint(newSegments.get(key), null))) changedSegments.add(key);

        Map<String, Feature> allFeatures = new HashMap<>(previousFeatures); allFeatures.putAll(newFeatures);
        boolean updated;
        do {
            updated = false;
            for (Map.Entry<String, Feature> entry : allFeatures.entrySet()) {
                if (changedFeatures.contains(entry.getKey())) continue;
                List<Set<String>> dependencies = featureDependencies(entry.getValue());
                if (!java.util.Collections.disjoint(dependencies.get(0), changedSegments)
                        || !java.util.Collections.disjoint(dependencies.get(1), changedFeatures)) {
                    changedFeatures.add(entry.getKey()); updated = true;
                }
            }
        } while (updated);

        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("revision", newRevision);
        details.put("previousRevision", previousRevision);
        details.put("revisionChanged", !(previousRevision == null ? newRevision == null : previousRevision.equals(newRevision)));
        Set<String> variableKeys = new HashSet<>();
        Map<String, GlobalVariable> previousVariables = previousDatafileContent.getVariables() != null ? previousDatafileContent.getVariables() : java.util.Collections.emptyMap();
        Map<String, GlobalVariable> newVariables = newDatafileContent.getVariables() != null ? newDatafileContent.getVariables() : java.util.Collections.emptyMap();
        for (String key : previousVariables.keySet()) {
            if (!newVariables.containsKey(key) || !Objects.equals(fingerprint(previousVariables.get(key), previousVariables.get(key).getHash()), fingerprint(newVariables.get(key), newVariables.get(key).getHash()))) variableKeys.add(key);
        }
        for (String key : newVariables.keySet()) if (!previousVariables.containsKey(key)) variableKeys.add(key);
        Map<String, GlobalVariable> allVariables = new HashMap<>(previousVariables); allVariables.putAll(newVariables);
        for (Map.Entry<String, GlobalVariable> entry : allVariables.entrySet()) {
            if (variableKeys.contains(entry.getKey())) continue;
            List<Set<String>> dependencies = variableDependencies(entry.getValue());
            if (!java.util.Collections.disjoint(dependencies.get(0), changedSegments)
                    || !java.util.Collections.disjoint(dependencies.get(1), changedFeatures)) variableKeys.add(entry.getKey());
        }
        details.put("features", new ArrayList<>(changedFeatures));
        details.put("variables", new ArrayList<>(variableKeys));
        details.put("replaced", replace);

        return details;
    }
}
