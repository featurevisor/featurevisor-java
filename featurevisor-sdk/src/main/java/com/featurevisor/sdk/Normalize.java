package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts selectors without changing definitions or their value ownership. */
final class Normalize {
    static final Object INVALID = java.util.Collections.unmodifiableMap(Map.of("or", List.of()));
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static final class Result<T> {
        final T value;
        final List<Map<String, Object>> errors;
        Result(T value, List<Map<String, Object>> errors) { this.value = value; this.errors = errors; }
    }

    private final String kind;
    private final String key;
    private final boolean legacy;
    private final List<Map<String, Object>> errors = new ArrayList<>();

    private Normalize(String kind, String key, boolean legacy) {
        this.kind = kind; this.key = key; this.legacy = legacy;
    }

    @SuppressWarnings("unchecked")
    static <T> T copy(T source) {
        if (source == null) return null;
        try {
            T result = (T) source.getClass().getDeclaredConstructor().newInstance();
            for (Field field : source.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(result, field.get(source));
            }
            return result;
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException(error); }
    }

    static boolean stringified(String value) {
        String text = value.stripLeading();
        return text.startsWith("{") || text.startsWith("[") || text.startsWith("\"");
    }

    private Object parse(Object value) {
        if (!(value instanceof String) || !stringified((String) value)) return value;
        try { return JSON.readValue((String) value, Object.class); }
        catch (Exception error) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("conditions", value);
            details.put("entityType", kind);
            details.put("entityKey", key);
            details.put("originalError", error);
            errors.add(details);
            return INVALID;
        }
    }

    private static Object old(Object value, boolean features) {
        if (value == INVALID) return value;
        if (value instanceof String) return features ? Map.of("feature", value) : value;
        if (value instanceof List) {
            List<?> values = (List<?>) value;
            if (values.isEmpty()) return "*";
            List<Object> result = new ArrayList<>();
            for (Object child : values) result.add(old(child, features));
            return result;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (String group : List.of("and", "or", "not")) {
                if (!(map.get(group) instanceof List)) continue;
                List<?> children = (List<?>) map.get(group);
                if (group.equals("and") && children.isEmpty()) return "*";
                List<Object> result = new ArrayList<>();
                for (Object child : children) result.add(old(child, features));
                return Map.of(group, result);
            }
        }
        return value;
    }

    private Object select(Object conditions, Object segments, List<Object> features, boolean oldConditions) {
        return select(conditions, segments, features, oldConditions, conditions != null);
    }

    private Object select(Object conditions, Object segments, List<Object> features, boolean oldConditions, boolean present) {
        List<Object> parts = new ArrayList<>();
        if (present) {
            Object parsed = parse(conditions);
            parts.add(oldConditions ? old(parsed, false) : parsed);
        }
        if (segments != null) parts.add(old(parse(segments), false));
        if (features != null && !features.isEmpty()) parts.add(old(features, true));
        for (Object part : parts) if (part == INVALID) return INVALID;
        if (parts.isEmpty()) return null;
        if (parts.size() == 1) return parts.get(0);
        List<Object> combined = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof List && !((List<?>) part).isEmpty()) combined.addAll((List<?>) part);
            else combined.add(part instanceof List ? Map.of("and", List.of()) : part);
        }
        return combined;
    }

    private VariableOverride override(VariableOverride source, boolean oldConditions) {
        VariableOverride value = copy(source);
        value.setConditions(select(source.getConditions(), source.getSegments(), source.getRequiredFeatures(), oldConditions));
        value.setSegments(null); value.setRequiredFeatures(null); value.setRequiredVariables(null);
        return value;
    }

    private List<VariableOverride> overrides(List<VariableOverride> entries, boolean oldConditions) {
        if (entries == null) return null;
        List<VariableOverride> result = new ArrayList<>();
        for (VariableOverride entry : entries) result.add(override(entry, oldConditions));
        return result;
    }

    private Map<String, List<VariableOverride>> groups(Map<String, List<VariableOverride>> entries) {
        if (entries == null) return null;
        Map<String, List<VariableOverride>> result = new LinkedHashMap<>();
        entries.forEach((name, values) -> result.put(name, overrides(values, legacy)));
        return result;
    }

    static Result<Feature> feature(String key, Feature source, boolean legacy) {
        Normalize n = new Normalize("feature", key, legacy);
        Feature value = copy(source);
        Object conditions = n.select(source.getConditions(), null, source.getRequiredFeatures(), false, source.hasConditions());
        if (conditions != null) value.setConditions(conditions);
        value.setRequiredFeatures(null); value.setRequiredVariables(null);
        if (source.getForce() != null) {
            List<Force> forces = new ArrayList<>();
            for (Force entry : source.getForce()) {
                Force force = copy(entry);
                force.setConditions(n.select(entry.getConditions(), entry.getSegments(), entry.getRequiredFeatures(), legacy));
                force.setSegments(null); force.setRequiredFeatures(null);
                forces.add(force);
            }
            value.setForce(forces);
        }
        if (source.getTraffic() != null) {
            List<Traffic> traffic = new ArrayList<>();
            for (Traffic entry : source.getTraffic()) {
                Traffic rule = copy(entry);
                rule.setConditions(n.select(entry.getConditions(), entry.getSegments(), entry.getRequiredFeatures(), false));
                rule.setSegments(null); rule.setRequiredFeatures(null);
                rule.setVariableOverrides(n.groups(entry.getVariableOverrides()));
                traffic.add(rule);
            }
            value.setTraffic(traffic);
        }
        if (source.getVariations() != null) {
            List<Variation> variations = new ArrayList<>();
            for (Variation entry : source.getVariations()) {
                Variation variation = copy(entry);
                variation.setVariableOverrides(n.groups(entry.getVariableOverrides()));
                variations.add(variation);
            }
            value.setVariations(variations);
        }
        return new Result<>(value, n.errors);
    }

    static Result<Segment> segment(String key, Segment source, boolean legacy) {
        Normalize n = new Normalize("segment", key, legacy);
        Segment value = copy(source);
        Object conditions = n.parse(source.getConditions());
        value.setConditions(legacy ? old(conditions, false) : conditions);
        return new Result<>(value, n.errors);
    }

    static Result<GlobalVariable> variable(String key, GlobalVariable source, boolean legacy) {
        Normalize n = new Normalize("variable", key, legacy);
        GlobalVariable value = copy(source);
        Object conditions = n.select(source.getConditions(), null, source.getRequiredFeatures(), false, source.hasConditions());
        if (conditions != null) value.setConditions(conditions);
        value.setRequiredFeatures(null); value.setRequiredVariables(null);
        value.setOverrides(n.overrides(source.getOverrides(), legacy));
        value.setOverrideProgram(n.overrides(source.getOverrideProgram(), false));
        return new Result<>(value, n.errors);
    }

    static DatafileContent incoming(DatafileContent source, List<Map<String, Object>> errors) {
        DatafileContent result = copy(source);
        if (!"2".equals(source.getSchemaVersion())) return result;
        Map<String, Feature> features = new LinkedHashMap<>();
        source.getFeatures().forEach((key, value) -> {
            Result<Feature> normalized = feature(key, value, true);
            features.put(key, normalized.value); errors.addAll(normalized.errors);
        });
        Map<String, Segment> segments = new LinkedHashMap<>();
        source.getSegments().forEach((key, value) -> {
            Result<Segment> normalized = segment(key, value, true);
            segments.put(key, normalized.value); errors.addAll(normalized.errors);
        });
        Map<String, GlobalVariable> variables = new LinkedHashMap<>();
        if (source.getVariables() != null) source.getVariables().forEach((key, value) -> {
            Result<GlobalVariable> normalized = variable(key, value, true);
            variables.put(key, normalized.value); errors.addAll(normalized.errors);
        });
        result.setFeatures(features); result.setSegments(segments); result.setVariables(variables);
        return result;
    }
}
