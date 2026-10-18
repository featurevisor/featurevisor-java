package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FeatureAllocationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> flag(String key, int percentage) {
        return new HashMap<>(Map.of(
            "key", key, "bucketBy", "userId",
            "traffic", List.of(new HashMap<>(Map.of(
                "key", "1", "segments", "*", "percentage", percentage, "allocation", List.of())))));
    }

    private DatafileContent datafile(Map<String, ?> features) throws Exception {
        return DatafileContent.fromJson(mapper.writeValueAsString(Map.of(
            "schemaVersion", "2", "revision", "1", "segments", Map.of(), "features", features)));
    }

    @Test
    public void legacyRangesAreUnknownFieldsWithoutWarnings() throws Exception {
        for (Object ranges : Arrays.asList(null, List.of(), List.of(List.of(50000, 100000)),
                List.of(List.of(0, 100000)), "ignored")) {
            for (int percentage : new int[] {0, 50000, 100000}) {
                for (Boolean enabled : Arrays.asList(null, false, true)) {
                    Map<String, Object> feature = flag("test", percentage);
                    DatafileContent baselineData = datafile(Map.of("test", feature));
                    baselineData.getFeatures().get("test").getTraffic().get(0).setEnabled(enabled);
                    Featurevisor baseline = Featurevisor.createFeaturevisor(
                        new Featurevisor.FeaturevisorOptions().datafile(baselineData).logLevel(FeaturevisorLogLevel.WARN));
                    feature.put("ranges", ranges);
                    DatafileContent legacyData = datafile(Map.of("test", feature));
                    legacyData.getFeatures().get("test").getTraffic().get(0).setEnabled(enabled);
                    List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
                    Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                        .datafile(legacyData).logLevel(FeaturevisorLogLevel.WARN).onDiagnostic(diagnostics::add));
                    for (int user = 0; user < 32; user++) {
                        Map<String, Object> context = Map.of("userId", "user-" + user);
                        assertEquals(mapper.valueToTree(baseline.evaluateFlag("test", context)),
                            mapper.valueToTree(sdk.evaluateFlag("test", context)));
                    }
                    assertTrue(diagnostics.isEmpty());
                    assertFalse(mapper.valueToTree(legacyData.getFeatures().get("test")).has("ranges"));
                    sdk.close();
                    baseline.close();
                }
            }
        }
    }

    @Test
    public void requiredVariationsAndPriorityExclusionUseNormalHashing() throws Exception {
        Map<String, Map<String, Object>> features = new HashMap<>();
        for (String key : List.of("cohort", "experimentA", "experimentB", "priority", "fallback")) {
            features.put(key, flag(key, key.equals("priority") ? 50000 : 100000));
        }
        features.get("cohort").put("variations", List.of(
            Map.of("value", "a"), Map.of("value", "b"), Map.of("value", "holdout")));
        features.get("cohort").put("traffic", List.of(Map.of(
            "key", "1", "segments", "*", "percentage", 100000, "allocation", List.of(
                Map.of("variation", "a", "range", List.of(0, 40000)),
                Map.of("variation", "b", "range", List.of(40000, 80000)),
                Map.of("variation", "holdout", "range", List.of(80000, 100000))))));
        features.get("experimentA").put("requiredFeatures", List.of(Map.of("feature", "cohort", "variation", "a")));
        features.get("experimentB").put("requiredFeatures", List.of(Map.of("feature", "cohort", "variation", "b")));
        features.get("fallback").put("requiredFeatures", List.of(Map.of("feature", "priority", "enabled", false)));
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile(features)).logLevel(FeaturevisorLogLevel.WARN));
        Map<String, Integer> counts = new HashMap<>(Map.of("a", 0, "b", 0, "holdout", 0, "priority", 0, "fallback", 0));
        int cohortOverlap = 0;
        int priorityOverlap = 0;
        for (int user = 0; user < 20000; user++) {
            Map<String, Object> context = Map.of("userId", "user-" + user);
            String variation = sdk.getVariation("cohort", context);
            assertTrue(List.of("a", "b", "holdout").contains(variation));
            counts.merge(variation, 1, Integer::sum);
            boolean a = sdk.isEnabled("experimentA", context);
            boolean b = sdk.isEnabled("experimentB", context);
            assertEquals("a".equals(variation), a);
            assertEquals("b".equals(variation), b);
            if (a && b) cohortOverlap++;
            assertEquals("holdout".equals(variation), !a && !b);
            boolean priority = sdk.isEnabled("priority", context);
            boolean fallback = sdk.isEnabled("fallback", context);
            assertNotEquals(priority, fallback);
            if (priority && fallback) priorityOverlap++;
            counts.merge(priority ? "priority" : "fallback", 1, Integer::sum);
        }
        assertEquals(0, cohortOverlap);
        assertEquals(0, priorityOverlap);
        assertEquals(20000, counts.get("a") + counts.get("b") + counts.get("holdout"));
        assertEquals(20000, counts.get("priority") + counts.get("fallback"));
        // Allow two percentage points around each intended population share.
        Map.of("a", 8000, "b", 8000, "holdout", 4000, "priority", 10000, "fallback", 10000)
            .forEach((key, expected) -> assertTrue(Math.abs(counts.get(key) - expected) <= 400, counts.toString()));
        sdk.close();
    }
}
