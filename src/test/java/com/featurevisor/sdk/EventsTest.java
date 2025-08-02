package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class EventsTest {

    @Test
    public void testGetParamsForStickySetEventEmptyToNew() {
        Map<String, Object> previousStickyFeatures = new HashMap<>();
        Map<String, Object> newStickyFeatures = new HashMap<>();
        newStickyFeatures.put("feature2", Map.of("enabled", true));
        newStickyFeatures.put("feature3", Map.of("enabled", true));
        boolean replace = true;

        Emitter.EventDetails result = Events.getParamsForStickySetEvent(
            previousStickyFeatures, newStickyFeatures, replace);

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) result.get("features");
        Boolean replaced = (Boolean) result.get("replaced");

        assertEquals(2, features.size());
        assertTrue(features.contains("feature2"));
        assertTrue(features.contains("feature3"));
        assertEquals(replace, replaced);
    }

    @Test
    public void testGetParamsForStickySetEventAddChangeRemove() {
        Map<String, Object> previousStickyFeatures = new HashMap<>();
        previousStickyFeatures.put("feature1", Map.of("enabled", true));
        previousStickyFeatures.put("feature2", Map.of("enabled", true));

        Map<String, Object> newStickyFeatures = new HashMap<>();
        newStickyFeatures.put("feature2", Map.of("enabled", true));
        newStickyFeatures.put("feature3", Map.of("enabled", true));

        boolean replace = true;

        Emitter.EventDetails result = Events.getParamsForStickySetEvent(
            previousStickyFeatures, newStickyFeatures, replace);

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) result.get("features");
        Boolean replaced = (Boolean) result.get("replaced");

        assertEquals(3, features.size());
        assertTrue(features.contains("feature1"));
        assertTrue(features.contains("feature2"));
        assertTrue(features.contains("feature3"));
        assertEquals(replace, replaced);
    }

    @Test
    public void testGetParamsForStickySetEventWithNullInputs() {
        // Test with null inputs
        Emitter.EventDetails result = Events.getParamsForStickySetEvent(null, null, false);

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) result.get("features");
        Boolean replaced = (Boolean) result.get("replaced");

        assertEquals(0, features.size());
        assertEquals(false, replaced);
    }

    @Test
    public void testGetParamsForDatafileSetEventEmptyToNew() {
        // Create empty previous datafile
        DatafileContent previousDatafileContent = new DatafileContent();
        previousDatafileContent.setSchemaVersion("1.0.0");
        previousDatafileContent.setRevision("1");
        previousDatafileContent.setFeatures(new HashMap<>());
        previousDatafileContent.setSegments(new HashMap<>());

        // Create new datafile with features
        DatafileContent newDatafileContent = new DatafileContent();
        newDatafileContent.setSchemaVersion("1.0.0");
        newDatafileContent.setRevision("2");

        Map<String, Feature> features = new HashMap<>();

        Feature feature1 = new Feature();
        feature1.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature1.setHash("hash1");
        feature1.setTraffic(new ArrayList<>());
        features.put("feature1", feature1);

        Feature feature2 = new Feature();
        feature2.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature2.setHash("hash2");
        feature2.setTraffic(new ArrayList<>());
        features.put("feature2", feature2);

        newDatafileContent.setFeatures(features);
        newDatafileContent.setSegments(new HashMap<>());

        Emitter.EventDetails result = Events.getParamsForDatafileSetEvent(
            previousDatafileContent, newDatafileContent);

        String revision = (String) result.get("revision");
        String previousRevision = (String) result.get("previousRevision");
        Boolean revisionChanged = (Boolean) result.get("revisionChanged");
        @SuppressWarnings("unchecked")
        List<String> featuresList = (List<String>) result.get("features");

        assertEquals("2", revision);
        assertEquals("1", previousRevision);
        assertEquals(true, revisionChanged);
        assertEquals(2, featuresList.size());
        assertTrue(featuresList.contains("feature1"));
        assertTrue(featuresList.contains("feature2"));
    }

    @Test
    public void testGetParamsForDatafileSetEventChangeHashAddition() {
        // Create previous datafile with features
        DatafileContent previousDatafileContent = new DatafileContent();
        previousDatafileContent.setSchemaVersion("1.0.0");
        previousDatafileContent.setRevision("1");

        Map<String, Feature> previousFeatures = new HashMap<>();

        Feature feature1 = new Feature();
        feature1.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature1.setHash("hash-same");
        feature1.setTraffic(new ArrayList<>());
        previousFeatures.put("feature1", feature1);

        Feature feature2 = new Feature();
        feature2.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature2.setHash("hash1-2");
        feature2.setTraffic(new ArrayList<>());
        previousFeatures.put("feature2", feature2);

        previousDatafileContent.setFeatures(previousFeatures);
        previousDatafileContent.setSegments(new HashMap<>());

        // Create new datafile with changed and added features
        DatafileContent newDatafileContent = new DatafileContent();
        newDatafileContent.setSchemaVersion("1.0.0");
        newDatafileContent.setRevision("2");

        Map<String, Feature> newFeatures = new HashMap<>();

        Feature newFeature1 = new Feature();
        newFeature1.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        newFeature1.setHash("hash-same"); // Same hash
        newFeature1.setTraffic(new ArrayList<>());
        newFeatures.put("feature1", newFeature1);

        Feature newFeature2 = new Feature();
        newFeature2.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        newFeature2.setHash("hash2-2"); // Changed hash
        newFeature2.setTraffic(new ArrayList<>());
        newFeatures.put("feature2", newFeature2);

        Feature feature3 = new Feature();
        feature3.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature3.setHash("hash2-3");
        feature3.setTraffic(new ArrayList<>());
        newFeatures.put("feature3", feature3); // New feature

        newDatafileContent.setFeatures(newFeatures);
        newDatafileContent.setSegments(new HashMap<>());

        Emitter.EventDetails result = Events.getParamsForDatafileSetEvent(
            previousDatafileContent, newDatafileContent);

        String revision = (String) result.get("revision");
        String previousRevision = (String) result.get("previousRevision");
        Boolean revisionChanged = (Boolean) result.get("revisionChanged");
        @SuppressWarnings("unchecked")
        List<String> featuresList = (List<String>) result.get("features");

        assertEquals("2", revision);
        assertEquals("1", previousRevision);
        assertEquals(true, revisionChanged);
        assertEquals(2, featuresList.size());
        assertTrue(featuresList.contains("feature2")); // Changed
        assertTrue(featuresList.contains("feature3")); // Added
        assertFalse(featuresList.contains("feature1")); // Not changed (same hash)
    }

    @Test
    public void testGetParamsForDatafileSetEventChangeHashRemoval() {
        // Create previous datafile with features
        DatafileContent previousDatafileContent = new DatafileContent();
        previousDatafileContent.setSchemaVersion("1.0.0");
        previousDatafileContent.setRevision("1");

        Map<String, Feature> previousFeatures = new HashMap<>();

        Feature feature1 = new Feature();
        feature1.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature1.setHash("hash-same");
        feature1.setTraffic(new ArrayList<>());
        previousFeatures.put("feature1", feature1);

        Feature feature2 = new Feature();
        feature2.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        feature2.setHash("hash1-2");
        feature2.setTraffic(new ArrayList<>());
        previousFeatures.put("feature2", feature2);

        previousDatafileContent.setFeatures(previousFeatures);
        previousDatafileContent.setSegments(new HashMap<>());

        // Create new datafile with one feature removed and one changed
        DatafileContent newDatafileContent = new DatafileContent();
        newDatafileContent.setSchemaVersion("1.0.0");
        newDatafileContent.setRevision("2");

        Map<String, Feature> newFeatures = new HashMap<>();

        Feature newFeature2 = new Feature();
        newFeature2.setBucketBy(new com.featurevisor.types.Bucket("userId"));
        newFeature2.setHash("hash2-2"); // Changed hash
        newFeature2.setTraffic(new ArrayList<>());
        newFeatures.put("feature2", newFeature2);
        // feature1 is removed

        newDatafileContent.setFeatures(newFeatures);
        newDatafileContent.setSegments(new HashMap<>());

        Emitter.EventDetails result = Events.getParamsForDatafileSetEvent(
            previousDatafileContent, newDatafileContent);

        String revision = (String) result.get("revision");
        String previousRevision = (String) result.get("previousRevision");
        Boolean revisionChanged = (Boolean) result.get("revisionChanged");
        @SuppressWarnings("unchecked")
        List<String> featuresList = (List<String>) result.get("features");

        assertEquals("2", revision);
        assertEquals("1", previousRevision);
        assertEquals(true, revisionChanged);
        assertEquals(2, featuresList.size());
        assertTrue(featuresList.contains("feature1")); // Removed
        assertTrue(featuresList.contains("feature2")); // Changed
    }

    @Test
    public void testGetParamsForDatafileSetEventWithNullInputs() {
        // Test with null inputs
        Emitter.EventDetails result = Events.getParamsForDatafileSetEvent(null, null);

        String revision = (String) result.get("revision");
        String previousRevision = (String) result.get("previousRevision");
        Boolean revisionChanged = (Boolean) result.get("revisionChanged");
        @SuppressWarnings("unchecked")
        List<String> featuresList = (List<String>) result.get("features");

        assertNull(revision);
        assertNull(previousRevision);
        assertEquals(false, revisionChanged);
        assertEquals(0, featuresList.size());
    }

    @Test
    public void testGetParamsForDatafileSetEventSameRevision() {
        // Create datafiles with same revision
        DatafileContent previousDatafileContent = new DatafileContent();
        previousDatafileContent.setRevision("1");
        previousDatafileContent.setFeatures(new HashMap<>());

        DatafileContent newDatafileContent = new DatafileContent();
        newDatafileContent.setRevision("1");
        newDatafileContent.setFeatures(new HashMap<>());

        Emitter.EventDetails result = Events.getParamsForDatafileSetEvent(
            previousDatafileContent, newDatafileContent);

        Boolean revisionChanged = (Boolean) result.get("revisionChanged");
        assertEquals(false, revisionChanged);
    }
}
