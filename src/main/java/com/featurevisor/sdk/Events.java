package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.DatafileContent;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Event parameter utilities for Featurevisor SDK
 * Provides methods to generate event details for various SDK events
 */
public class Events {

    /**
     * Get parameters for sticky set event
     * @param previousStickyFeatures Previous sticky features map
     * @param newStickyFeatures New sticky features map
     * @param replace Whether the sticky features were replaced
     * @return Event details for sticky set event
     */
    public static Emitter.EventDetails getParamsForStickySetEvent(
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

        Emitter.EventDetails details = new Emitter.EventDetails();
        details.put("features", uniqueFeaturesAffected);
        details.put("replaced", replace);

        return details;
    }

    /**
     * Get parameters for datafile set event
     * @param previousDatafileContent Previous datafile content
     * @param newDatafileContent New datafile content
     * @return Event details for datafile set event
     */
    public static Emitter.EventDetails getParamsForDatafileSetEvent(
            DatafileContent previousDatafileContent,
            DatafileContent newDatafileContent) {

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

        // Results
        List<String> removedFeatures = new ArrayList<>();
        List<String> changedFeatures = new ArrayList<>();
        List<String> addedFeatures = new ArrayList<>();

        // Check against existing datafile
        for (String previousFeatureKey : previousFeatureKeys) {
            if (!newFeatureKeys.contains(previousFeatureKey)) {
                // Feature was removed in new datafile
                removedFeatures.add(previousFeatureKey);
                continue;
            }

            // Feature exists in both datafiles, check if it was changed
            Feature previousFeature = previousDatafileContent.getFeatures().get(previousFeatureKey);
            Feature newFeature = newDatafileContent.getFeatures().get(previousFeatureKey);

            String previousHash = previousFeature != null ? previousFeature.getHash() : null;
            String newHash = newFeature != null ? newFeature.getHash() : null;

            if (previousHash == null ? newHash != null : !previousHash.equals(newHash)) {
                // Feature was changed in new datafile
                changedFeatures.add(previousFeatureKey);
            }
        }

        // Check against new datafile
        for (String newFeatureKey : newFeatureKeys) {
            if (!previousFeatureKeys.contains(newFeatureKey)) {
                // Feature was added in new datafile
                addedFeatures.add(newFeatureKey);
            }
        }

        // Combine all affected feature keys
        List<String> allAffectedFeatures = new ArrayList<>();
        allAffectedFeatures.addAll(removedFeatures);
        allAffectedFeatures.addAll(changedFeatures);
        allAffectedFeatures.addAll(addedFeatures);

        // Remove duplicates
        List<String> uniqueAffectedFeatures = allAffectedFeatures.stream()
                .distinct()
                .collect(Collectors.toList());

        Emitter.EventDetails details = new Emitter.EventDetails();
        details.put("revision", newRevision);
        details.put("previousRevision", previousRevision);
        details.put("revisionChanged", !(previousRevision == null ? newRevision == null : previousRevision.equals(newRevision)));
        details.put("features", uniqueAffectedFeatures);

        return details;
    }
}
