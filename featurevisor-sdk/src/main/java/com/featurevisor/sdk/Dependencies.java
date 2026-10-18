package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Structural mixed graph indexing, without reading normalized entity caches. */
final class Dependencies {
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final class Index {
        final Map<String, Set<String>>[] dependents;
        final Map<String, Set<String>> owners = new HashMap<>();
        private final Set<String> writableSources = new HashSet<>();
        int extractedEntities;
        @SuppressWarnings("unchecked")
        Index() {
            dependents = new Map[3];
            for (int index = 0; index < 3; index++) dependents[index] = new HashMap<>();
        }
        Index(Index previous) {
            this();
            for (int kind = 0; kind < 3; kind++) dependents[kind].putAll(previous.dependents[kind]);
            owners.putAll(previous.owners);
        }
        Set<String> writable(int kind, String key) {
            String source = id(kind, key);
            Set<String> values = dependents[kind].get(key);
            if (writableSources.add(source) || values == null) {
                values = values == null ? new HashSet<>() : new HashSet<>(values);
                dependents[kind].put(key, values);
            }
            return values;
        }
        void remove(String owner) {
            for (String source : owners.getOrDefault(owner, Set.of())) {
                int kind = source.charAt(0) - '0';
                String key = source.substring(2);
                Set<String> next = writable(kind, key);
                next.remove(owner);
                if (next.isEmpty()) dependents[kind].remove(key);
                else dependents[kind].put(key, next);
            }
            owners.remove(owner);
        }
    }
    private static String id(int kind, String key) { return kind + ":" + key; }
    private static void add(Index index, int kind, String key, String dependent) {
        if (key != null) {
            index.writable(kind, key).add(dependent);
            index.owners.computeIfAbsent(dependent, ignored -> new HashSet<>()).add(id(kind, key));
        }
    }
    private static void visit(Index index, Object value, String dependent) {
        if (value == null || value == Normalize.INVALID || "*".equals(value)) return;
        if (value instanceof String) {
            String text = (String) value;
            if (Normalize.stringified(text)) {
                try { visit(index, JSON.readValue(text, Object.class), dependent); }
                catch (Exception ignored) { }
            } else add(index, 2, text, dependent);
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) visit(index, child, dependent);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.get("variable") instanceof String) {
                if (map.get("feature") instanceof String) add(index, 0, (String) map.get("feature"), dependent);
                else add(index, 1, (String) map.get("variable"), dependent);
            } else if (map.get("feature") instanceof String) add(index, 0, (String) map.get("feature"), dependent);
            else if (!map.containsKey("attribute")) {
                for (String group : List.of("and", "or", "not")) if (map.containsKey(group)) visit(index, map.get(group), dependent);
            }
        }
    }
    private static void selectors(Index index, Object conditions, Object segments, List<Object> required, String dependent) {
        visit(index, conditions, dependent);
        visit(index, segments, dependent);
        legacy(index, required, dependent);
    }
    private static void legacy(Index index, Object value, String dependent) {
        if (value instanceof String) add(index, 0, (String) value, dependent);
        else if (value instanceof List) for (Object child : (List<?>) value) legacy(index, child, dependent);
        else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.get("feature") instanceof String) add(index, 0, (String) map.get("feature"), dependent);
            else for (String group : List.of("and", "or", "not")) legacy(index, map.get(group), dependent);
        }
    }
    private static void overrides(Index index, List<VariableOverride> values, String dependent) {
        if (values != null) for (VariableOverride value : values) {
            selectors(index, value.getConditions(), value.getSegments(), value.getRequiredFeatures(), dependent);
        }
    }
    private static void groups(Index index, Map<String, List<VariableOverride>> values, String dependent) {
        if (values != null) for (List<VariableOverride> entries : values.values()) overrides(index, entries, dependent);
    }
    static Index create(DatafileContent datafile) {
        return update(new Index(), new DatafileContent(), datafile);
    }
    static Index update(Index previous, DatafileContent before, DatafileContent datafile) {
        Index index = new Index(previous);
        updateOwners(index, 2, before.getSegments(), datafile.getSegments(), (key, value) -> visit(index, value.getConditions(), id(2, key)));
        updateOwners(index, 0, before.getFeatures(), datafile.getFeatures(), (key, value) -> {
            String dependent = id(0, key);
            selectors(index, value.getConditions(), null, value.getRequiredFeatures(), dependent);
            if (value.getForce() != null) for (Force force : value.getForce()) selectors(index, force.getConditions(), force.getSegments(), force.getRequiredFeatures(), dependent);
            if (value.getTraffic() != null) for (Traffic traffic : value.getTraffic()) {
                selectors(index, traffic.getConditions(), traffic.getSegments(), traffic.getRequiredFeatures(), dependent);
                groups(index, traffic.getVariableOverrides(), dependent);
            }
            if (value.getVariations() != null) for (Variation variation : value.getVariations()) groups(index, variation.getVariableOverrides(), dependent);
        });
        updateOwners(index, 1, before.getVariables(), datafile.getVariables(), (key, value) -> {
            String dependent = id(1, key);
            selectors(index, value.getConditions(), null, value.getRequiredFeatures(), dependent);
            Object compose = value.getCompose();
            if (compose instanceof String) add(index, 1, (String) compose, dependent);
            else if (compose instanceof List) for (Object entry : (List<?>) compose) visit(index, entry, dependent);
            overrides(index, value.getOverrideProgram() != null ? value.getOverrideProgram() : value.getOverrides(), dependent);
        });
        return index;
    }
    private static <T> void updateOwners(Index index, int kind, Map<String, T> before, Map<String, T> after,
            java.util.function.BiConsumer<String, T> extract) {
        Map<String, T> old = before == null ? Map.of() : before;
        Map<String, T> current = after == null ? Map.of() : after;
        old.forEach((key, value) -> {
            if (current.get(key) != value) index.remove(id(kind, key));
        });
        current.forEach((key, value) -> {
            if (old.get(key) != value) {
                index.extractedEntities++;
                extract.accept(key, value);
            }
        });
    }
    static void addChanges(Set<String> features, Set<String> variables, Set<String> changedSegments, Index index) {
        Set<String> seen = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        features.forEach(key -> queue.add(id(0, key)));
        variables.forEach(key -> queue.add(id(1, key)));
        changedSegments.forEach(key -> queue.add(id(2, key)));
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!seen.add(current)) continue;
            int kind = current.charAt(0) - '0';
            String key = current.substring(2);
            if (kind == 0) features.add(key);
            if (kind == 1) variables.add(key);
            for (String dependent : index.dependents[kind].getOrDefault(key, Set.of())) queue.add(dependent);
        }
    }
}
