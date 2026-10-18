package com.featurevisor.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.Segment;
import com.featurevisor.sdk.Traffic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SDK gates and structural preflight for compiled execution protocol segments. */
final class SegmentEvaluations {
    private static final ObjectMapper JSON = new ObjectMapper();
    final DatafileContent datafile;
    final Map<String, String> featureKeys = new LinkedHashMap<>();

    SegmentEvaluations(DatafileContent source, Map<String, Object> definitions) {
        datafile = new DatafileContent(source.getSchemaVersion(), source.getRevision());
        datafile.setFeaturevisorVersion(source.getFeaturevisorVersion());
        datafile.setVariables(source.getVariables());
        Map<String, Segment> segments = new LinkedHashMap<>();
        if (source.getSegments() != null) segments.putAll(source.getSegments());
        definitions.forEach((key, value) -> {
            if (!segments.containsKey(key)) segments.put(key, JSON.convertValue(value, Segment.class));
        });
        datafile.setSegments(segments);
        Map<String, Feature> features = new LinkedHashMap<>();
        if (source.getFeatures() != null) features.putAll(source.getFeatures());
        for (String key : definitions.keySet()) {
            String helper = "__featurevisor_segment_test__" + key;
            while (features.containsKey(helper)) helper += "_";
            Feature feature = new Feature(helper);
            feature.setConditions(key);
            feature.setBucketBy(new com.featurevisor.sdk.Bucket("__segment_test__"));
            Traffic traffic = new Traffic("segment");
            traffic.setConditions("*"); traffic.setPercentage(100000);
            feature.setTraffic(List.of(traffic));
            features.put(helper, feature);
            featureKeys.put(key, helper);
        }
        datafile.setFeatures(features);
    }

    static String validate(String segment, DatafileContent datafile, Map<String, Object> definitions) {
        try {
            List<Map<String, Object>> pending = new ArrayList<>();
            pending.add(Map.of("segment", segment));
            Set<String> seen = new HashSet<>();
            for (int cursor = 0; cursor < pending.size(); cursor++) {
                Map<String, Object> reference = pending.get(cursor);
                String id = JSON.writeValueAsString(reference);
                if (!seen.add(id)) continue;
                if (reference.containsKey("segment")) {
                    String key = (String) reference.get("segment");
                    Object value = definitions.get(key);
                    if (value == null && datafile.getSegments() != null) value = datafile.getSegments().get(key);
                    if (value == null) return "segment \"" + key + "\" definition is missing";
                    Map<?, ?> entry = JSON.convertValue(value, Map.class);
                    if (!entry.containsKey("conditions")) return "segment \"" + key + "\" definition has no conditions";
                    visit(entry.get("conditions"), pending, false);
                } else if (reference.containsKey("feature")) {
                    String key = (String) reference.get("feature");
                    Feature feature = datafile.getFeature(key);
                    if (feature == null) return "feature \"" + key + "\" is absent from the selected datafile";
                    Object variable = reference.get("variable");
                    if (variable != null && (feature.getVariablesSchema() == null || !feature.getVariablesSchema().containsKey(variable))) {
                        return "feature variable \"" + key + "." + variable + "\" is absent from the selected datafile";
                    }
                    selectors(JSON.convertValue(feature, Map.class), pending);
                    if (feature.getForce() != null) for (Object entry : feature.getForce()) selectors(JSON.convertValue(entry, Map.class), pending);
                    if (feature.getTraffic() != null) for (Traffic entry : feature.getTraffic()) {
                        selectors(JSON.convertValue(entry, Map.class), pending);
                        if (variable != null && entry.getVariableOverrides() != null) overrides(entry.getVariableOverrides().get(variable), pending);
                    }
                    if (variable != null && feature.getVariations() != null) feature.getVariations().forEach(entry -> {
                        if (entry.getVariableOverrides() != null) overrides(entry.getVariableOverrides().get(variable), pending);
                    });
                } else {
                    String key = (String) reference.get("variable");
                    Object value = datafile.getVariables() == null ? null : datafile.getVariables().get(key);
                    if (value == null) return "global variable \"" + key + "\" is absent from the selected datafile";
                    Map<?, ?> entry = JSON.convertValue(value, Map.class);
                    selectors(entry, pending);
                    overrides(entry.get("overrides"), pending); overrides(entry.get("overrideProgram"), pending);
                    Object compose = entry.get("compose");
                    if (compose instanceof String) pending.add(Map.of("variable", compose));
                    else visit(compose, pending, false);
                }
            }
            return null;
        } catch (Exception error) { return "invalid segment dependency: " + error.getMessage(); }
    }

    private static void selectors(Map<?, ?> value, List<Map<String, Object>> pending) {
        visit(value.get("conditions"), pending, false);
        visit(value.get("segments"), pending, false);
        visit(value.get("requiredFeatures"), pending, true);
    }
    private static void overrides(Object values, List<Map<String, Object>> pending) {
        if (values instanceof List) for (Object value : (List<?>) values) selectors(JSON.convertValue(value, Map.class), pending);
    }
    private static void visit(Object value, List<Map<String, Object>> pending, boolean legacy) {
        if (value instanceof String) {
            String text = (String) value;
            if (text.equals("*")) return;
            if (text.stripLeading().matches("(?s)^[\\[{\"].*")) {
                try { visit(JSON.readValue(text, Object.class), pending, legacy); }
                catch (Exception error) { throw new IllegalArgumentException("invalid conditions", error); }
            } else pending.add(Map.of(legacy ? "feature" : "segment", text));
        } else if (value instanceof List) for (Object child : (List<?>) value) visit(child, pending, legacy);
        else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.get("variable") instanceof String || map.get("feature") instanceof String) {
                Map<String, Object> reference = new LinkedHashMap<>();
                if (map.get("variable") instanceof String) reference.put("variable", map.get("variable"));
                if (map.get("feature") instanceof String) reference.put("feature", map.get("feature"));
                pending.add(reference);
            } else if (!map.containsKey("attribute")) for (String group : List.of("and", "or", "not")) visit(map.get(group), pending, legacy);
        }
    }
}
