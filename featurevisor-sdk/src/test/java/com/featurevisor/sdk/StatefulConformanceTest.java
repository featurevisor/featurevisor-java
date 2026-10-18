package com.featurevisor.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatefulConformanceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runsEveryStatefulConformanceCase() throws Exception {
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/conformance/sdk-stateful.json")) {
            assertNotNull(stream);
            fixture = json.readTree(stream);
        }
        assertEquals("featurevisor.sdk-stateful", fixture.get("kind").asText());
        assertEquals(1, fixture.get("schemaVersion").asInt());
        assertTrue(fixture.get("cases").isArray());
        assertFalse(fixture.get("cases").isEmpty());

        for (JsonNode testCase : fixture.get("cases")) {
            runCase(testCase);
        }
    }

    private void runCase(JsonNode testCase) throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        DatafileContent initial = DatafileContent.fromJson(
            json.writeValueAsString(testCase.at("/initial/datafile"))
        );
        Featurevisor root = Featurevisor.createFeaturevisor(
            new Featurevisor.FeaturevisorOptions()
                .datafile(initial)
                .context(map(testCase.at("/initial/context")))
                .onDiagnostic(diagnostics::add)
                .logLevel(FeaturevisorLogLevel.DEBUG)
        );
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("root", root);
        subscribe(root, "root", events);
        try {
            for (JsonNode operation : testCase.get("operations")) {
                events.clear();
                diagnostics.clear();
                execute(operation, targets, events);
                assertOperation(testCase.get("name").asText(), operation, targets, events, diagnostics);
            }
        } finally {
            for (Object target : targets.values()) {
                if (target instanceof ChildInstance) ((ChildInstance) target).close();
            }
            root.close();
        }
    }

    private void execute(JsonNode operation, Map<String, Object> targets,
            List<Map<String, Object>> events) throws Exception {
        String action = operation.get("action").asText();
        if (action.equals("setDatafile")) {
            Featurevisor root = (Featurevisor) targets.get(operation.get("target").asText());
            boolean replace = operation.path("replace").asBoolean(false);
            JsonNode raw = operation.get("datafile");
            if (raw.isTextual()) {
                root.setDatafile(raw.asText(), replace);
            } else {
                root.setDatafile(DatafileContent.fromJson(json.writeValueAsString(raw)), replace);
            }
            return;
        }
        if (action.equals("spawn")) {
            Featurevisor parent = (Featurevisor) targets.get(operation.get("parent").asText());
            JsonNode options = operation.path("options");
            Featurevisor.SpawnOptions spawnOptions = new Featurevisor.SpawnOptions()
                .stickyFeatures(map(options.get("stickyFeatures")))
                .stickyVariables(map(options.get("stickyVariables")));
            ChildInstance child = parent.spawn(map(operation.get("context")), spawnOptions);
            String targetName = operation.get("target").asText();
            targets.put(targetName, child);
            subscribe(child, targetName, events);
            return;
        }
        Object target = targets.get(operation.get("target").asText());
        boolean replace = operation.path("replace").asBoolean(false);
        if (action.equals("setStickyFeatures")) {
            setStickyFeatures(target, map(operation.get("stickyFeatures")), replace);
        } else if (action.equals("setStickyVariables")) {
            setStickyVariables(target, map(operation.get("stickyVariables")), replace);
        } else {
            fail("Unknown stateful action " + action);
        }
    }

    private void assertOperation(String caseName, JsonNode operation, Map<String, Object> targets,
            List<Map<String, Object>> events, List<FeaturevisorDiagnostic> diagnostics) {
        JsonNode expect = operation.get("expect");
        if (expect.has("error")) {
            String code = expect.at("/error/code").asText();
            assertTrue(diagnostics.stream().anyMatch(value -> code.equals(value.getCode())),
                caseName + ": expected diagnostic " + code);
        } else {
            assertTrue(diagnostics.stream().noneMatch(value -> value.getLevel() == FeaturevisorLogLevel.ERROR),
                caseName + ": unexpected error diagnostic");
        }

        JsonNode expectedEvents = normalize(expect.get("events"));
        JsonNode actualEvents = normalize(json.valueToTree(events));
        assertEquals(expectedEvents, actualEvents, caseName + ": events for " + operation.get("action"));

        for (JsonNode result : expect.get("results")) {
            Object target = targets.get(result.get("target").asText());
            Object actual = method(target, result.get("method").asText(), result.get("args"));
            assertEquals(normalize(result.get("value")), normalize(json.valueToTree(actual)),
                caseName + ": " + result.get("target").asText() + "." + result.get("method").asText());
        }
    }

    private Object method(Object target, String method, JsonNode args) {
        if (method.equals("getRevision")) return ((Featurevisor) target).getRevision();
        if (method.equals("isEnabled")) {
            String feature = args.get(0).asText();
            return target instanceof Featurevisor
                ? ((Featurevisor) target).isEnabled(feature)
                : ((ChildInstance) target).isEnabled(feature);
        }
        if (method.equals("getVariable")) {
            if (args.size() == 1) {
                String variable = args.get(0).asText();
                return target instanceof Featurevisor
                    ? ((Featurevisor) target).getVariable(variable)
                    : ((ChildInstance) target).getVariable(variable);
            }
            String feature = args.get(0).asText();
            String variable = args.get(1).asText();
            return target instanceof Featurevisor
                ? ((Featurevisor) target).getVariable(feature, variable)
                : ((ChildInstance) target).getVariable(feature, variable);
        }
        throw new AssertionError("Unknown result method " + method);
    }

    private void subscribe(Featurevisor target, String name, List<Map<String, Object>> events) {
        for (FeaturevisorEventName event : new FeaturevisorEventName[] {
            FeaturevisorEventName.DATAFILE_SET,
            FeaturevisorEventName.STICKY_FEATURES_SET,
            FeaturevisorEventName.STICKY_VARIABLES_SET,
        }) {
            target.on(event, details -> events.add(event(name, event, details)));
        }
    }

    private void subscribe(ChildInstance target, String name, List<Map<String, Object>> events) {
        for (FeaturevisorEventName event : new FeaturevisorEventName[] {
            FeaturevisorEventName.STICKY_FEATURES_SET,
            FeaturevisorEventName.STICKY_VARIABLES_SET,
        }) {
            target.on(event, details -> events.add(event(name, event, details)));
        }
    }

    private Map<String, Object> event(String target, FeaturevisorEventName name,
            FeaturevisorEventDetails details) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("target", target);
        value.put("name", name.getValue());
        value.put("details", new LinkedHashMap<>(details));
        return value;
    }

    private void setStickyFeatures(Object target, Map<String, Object> value, boolean replace) {
        if (target instanceof Featurevisor) ((Featurevisor) target).setStickyFeatures(value, replace);
        else ((ChildInstance) target).setStickyFeatures(value, replace);
    }

    private void setStickyVariables(Object target, Map<String, Object> value, boolean replace) {
        if (target instanceof Featurevisor) ((Featurevisor) target).setStickyVariables(value, replace);
        else ((ChildInstance) target).setStickyVariables(value, replace);
    }

    private Map<String, Object> map(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return new LinkedHashMap<>();
        return json.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private JsonNode normalize(JsonNode value) {
        JsonNode copy = value == null ? json.nullNode() : value.deepCopy();
        normalizeKeySets(copy);
        return copy;
    }

    private void normalizeKeySets(JsonNode value) {
        if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                if ((entry.getKey().equals("features") || entry.getKey().equals("variables"))
                        && entry.getValue().isArray()) {
                    List<String> keys = new ArrayList<>();
                    entry.getValue().forEach(item -> keys.add(item.asText()));
                    Collections.sort(keys);
                    ArrayNode sorted = json.createArrayNode();
                    keys.forEach(sorted::add);
                    ((ObjectNode) value).set(entry.getKey(), sorted);
                } else {
                    normalizeKeySets(entry.getValue());
                }
            });
        } else if (value.isArray()) {
            value.forEach(this::normalizeKeySets);
        }
    }
}
