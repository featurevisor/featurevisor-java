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

    private static String fingerprint(Object value) {
        try { return OBJECT_MAPPER.writeValueAsString(value); }
        catch (Exception ignored) { return String.valueOf(value); }
    }

    private static boolean hashChanged(String before, String after) {
        return before == null || before.isEmpty() || after == null || after.isEmpty()
            || !before.equals(after);
    }

    /**
     * Get parameters for sticky set event
     * @param previousStickyFeatures Previous sticky features map
     * @param newStickyFeatures New sticky features map
     * @param replace Whether the sticky features were replaced
     * @return Event details for sticky set event
     */
    public static FeaturevisorEventDetails getParamsForStickyFeaturesSetEvent(
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
        return getParamsForDatafileSetEvent(previousDatafileContent, newDatafileContent, replace, null, null);
    }

    static FeaturevisorEventDetails getParamsForProviderSetEvent(
            InstanceEvaluationDataProvider previous, InstanceEvaluationDataProvider current, boolean replace) {
        return getParamsForDatafileSetEvent(previous.getDatafile(), current.getDatafile(), replace, previous, current);
    }

    private static FeaturevisorEventDetails getParamsForDatafileSetEvent(
            DatafileContent previousDatafileContent, DatafileContent newDatafileContent, boolean replace,
            InstanceEvaluationDataProvider previousProvider, InstanceEvaluationDataProvider currentProvider) {

        if (previousDatafileContent == null) {
            previousDatafileContent = new DatafileContent();
        }
        if (newDatafileContent == null) {
            newDatafileContent = new DatafileContent();
        }

        if ((previousDatafileContent.getFeatures() == null || previousDatafileContent.getFeatures().isEmpty())
                && (previousDatafileContent.getVariables() == null || previousDatafileContent.getVariables().isEmpty())
                && (previousDatafileContent.getSegments() == null || previousDatafileContent.getSegments().isEmpty())) {
            // Every incoming entity is already announced, so no dependency traversal is needed.
            FeaturevisorEventDetails details = new FeaturevisorEventDetails();
            details.put("revision", newDatafileContent.getRevision());
            details.put("previousRevision", previousDatafileContent.getRevision());
            details.put("revisionChanged", !Objects.equals(previousDatafileContent.getRevision(), newDatafileContent.getRevision()));
            details.put("features", newDatafileContent.getFeatures() == null ? new ArrayList<>() : new ArrayList<>(newDatafileContent.getFeatures().keySet()));
            details.put("variables", newDatafileContent.getVariables() == null ? new ArrayList<>() : new ArrayList<>(newDatafileContent.getVariables().keySet()));
            details.put("replaced", replace);
            return details;
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
            if (before == null || after == null || hashChanged(before.getHash(), after.getHash())) changedFeatures.add(key);
        }

        Map<String, Segment> previousSegments = previousDatafileContent.getSegments() != null ? previousDatafileContent.getSegments() : java.util.Collections.emptyMap();
        Map<String, Segment> newSegments = newDatafileContent.getSegments() != null ? newDatafileContent.getSegments() : java.util.Collections.emptyMap();
        Set<String> changedSegments = new HashSet<>();
        Set<String> allSegmentKeys = new HashSet<>(previousSegments.keySet()); allSegmentKeys.addAll(newSegments.keySet());
        for (String key : allSegmentKeys) {
            Segment before = previousSegments.get(key), after = newSegments.get(key);
            if (before != after && !Objects.equals(fingerprint(before), fingerprint(after))) changedSegments.add(key);
        }

        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("revision", newRevision);
        details.put("previousRevision", previousRevision);
        details.put("revisionChanged", !(previousRevision == null ? newRevision == null : previousRevision.equals(newRevision)));
        Set<String> variableKeys = new HashSet<>();
        Map<String, GlobalVariable> previousVariables = previousDatafileContent.getVariables() != null ? previousDatafileContent.getVariables() : java.util.Collections.emptyMap();
        Map<String, GlobalVariable> newVariables = newDatafileContent.getVariables() != null ? newDatafileContent.getVariables() : java.util.Collections.emptyMap();
        for (String key : previousVariables.keySet()) {
            if (!newVariables.containsKey(key) || hashChanged(previousVariables.get(key).getHash(), newVariables.get(key).getHash())) variableKeys.add(key);
        }
        for (String key : newVariables.keySet()) if (!previousVariables.containsKey(key)) variableKeys.add(key);
        Dependencies.addChanges(changedFeatures, variableKeys, changedSegments,
            previousProvider == null ? Dependencies.create(previousDatafileContent) : previousProvider.getDependencyIndex());
        Dependencies.addChanges(changedFeatures, variableKeys, changedSegments,
            currentProvider == null ? Dependencies.create(newDatafileContent) : currentProvider.deriveDependencyIndex(previousProvider));
        details.put("features", new ArrayList<>(changedFeatures));
        details.put("variables", new ArrayList<>(variableKeys));
        details.put("replaced", replace);

        return details;
    }
}
