package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeV4CapabilitiesTest {
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode fixture(String name) throws Exception {
        return json.readTree(getClass().getResourceAsStream("/conformance/" + name + ".json"));
    }
    private Map<String, Object> map(JsonNode value) {
        assertTrue(value == null || value.isMissingNode() || value.isObject(), "expected an object");
        return value == null || value.isMissingNode() ? Map.of() : json.convertValue(value, Map.class);
    }
    static void jsonEquals(JsonNode expected, JsonNode actual, String label) {
        assertNotNull(actual, label + ": missing value");
        if (expected.isNumber() && actual.isNumber()) {
            assertTrue(expected.doubleValue() == actual.doubleValue(), label + ": numeric value");
        } else if (expected.isObject() && actual.isObject()) {
            assertEquals(expected.size(), actual.size(), label);
            expected.fields().forEachRemaining(field ->
                jsonEquals(field.getValue(), actual.get(field.getKey()), label + "." + field.getKey()));
        } else if (expected.isArray() && actual.isArray()) {
            assertEquals(expected.size(), actual.size(), label);
            for (int i = 0; i < expected.size(); i++) jsonEquals(expected.get(i), actual.get(i), label + "[" + i + "]");
        } else {
            assertEquals(expected, actual, label);
        }
    }
    private void fields(JsonNode expected, Evaluation evaluation, String label) {
        assertTrue(expected != null && expected.isObject() && !expected.isEmpty(), label + " expected fields");
        JsonNode actual = json.valueToTree(evaluation);
        expected.fields().forEachRemaining(field -> {
            if (!field.getKey().equals("value")) {
                assertTrue(actual.has(field.getKey()), label + ": missing " + field.getKey());
                jsonEquals(field.getValue(), actual.get(field.getKey()), label + ": " + field.getKey());
            }
        });
    }

    @Test void diagnosticsFixtureChecksRevisionCacheAndReentry() throws Exception {
        JsonNode fixture = fixture("sdk-diagnostics");
        assertEquals("featurevisor.sdk-diagnostics", fixture.path("kind").textValue());
        assertEquals(1, fixture.path("version").intValue());
        assertTrue(fixture.path("cases").isArray() && !fixture.path("cases").isEmpty());
        List<JsonNode> repeatedCases = new ArrayList<>();
        fixture.get("cases").forEach(repeatedCases::add);
        fixture.get("cases").forEach(repeatedCases::add);
        for (JsonNode row : repeatedCases) {
            List<JsonNode> seen = new ArrayList<>();
            Featurevisor[] owner = new Featurevisor[1];
            boolean[] reentering = {false};
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(d -> {
                    if (!d.getCode().equals("conditions_parse_error")) return;
                    seen.add(json.valueToTree(Map.of("code", d.getCode(), "level", d.getLevel().name().toLowerCase(java.util.Locale.ROOT),
                        "details", d.getDetails(), "revision", owner[0].getRevision())));
                    if ("feature".equals(d.getDetails().get("entityType")) && !reentering[0]) {
                        reentering[0] = true;
                        try {
                            int before = seen.size();
                            owner[0].isEnabled((String) d.getDetails().get("entityKey"));
                            assertEquals(before, seen.size(), "reentry diagnostics");
                        } finally { reentering[0] = false; }
                    }
                }));
            owner[0] = f;
            try {
                assertTrue(row.path("operations").isArray() && !row.path("operations").isEmpty());
                for (JsonNode op : row.get("operations")) {
                    int before = seen.size();
                    boolean result;
                    switch (op.path("action").textValue()) {
                        case "setDatafile": result = f.setDatafile(op.get("datafile").toString(), op.get("replace").booleanValue()); break;
                        case "isEnabled": result = f.isEnabled(op.get("feature").textValue()); break;
                        default: throw new AssertionError("unsupported diagnostics action");
                    }
                    assertTrue(op.get("expectedResult").isBoolean());
                    assertEquals(op.get("expectedResult").booleanValue(), result, row.path("name").asText());
                    List<JsonNode> unmatched = new ArrayList<>(seen.subList(before, seen.size()));
                    for (JsonNode expected : op.get("diagnostics")) assertTrue(unmatched.remove(expected), "missing diagnostic " + expected);
                    assertTrue(unmatched.isEmpty(), "unexpected diagnostics " + unmatched);
                }
            } finally { f.close(); }
        }
    }

    @Test void allConditionsCasesTwiceWithPreorderDependenciesAndOrdinaryGetters() throws Exception {
        JsonNode fixture = fixture("sdk-conditions");
        assertEquals(1, fixture.path("version").asInt());
        assertEquals("unified-conditions", fixture.path("capability").asText());
        assertTrue(fixture.path("cases").isArray() && !fixture.path("cases").isEmpty());
        int[] executed = new int[2];
        for (JsonNode row : fixture.path("cases")) {
            List<Map<String, Object>> dependencies = new ArrayList<>();
            FeaturevisorModule module = new FeaturevisorModule("dependencies").beforeEvaluation(options -> {
                if (options.getDependencyPurpose() != null) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    if (options.getFeatureKey() != null) entry.put("feature", options.getFeatureKey());
                    if (options.getVariableKey() != null) entry.put("variable", options.getVariableKey());
                    entry.put("dependencyPurpose", options.getDependencyPurpose());
                    dependencies.add(entry);
                }
                return options;
            });
            DatafileContent datafile = DatafileContent.fromJson(row.get("datafile").toString());
            String before = datafile.toJson();
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile).logLevel(FeaturevisorLogLevel.FATAL).modules(List.of(module))
                .stickyFeatures(map(row.path("options").get("stickyFeatures")))
                .stickyVariables(map(row.path("options").get("stickyVariables"))));
            try {
                String label = row.path("name").asText();
                Map<String, Object> context = map(row.get("context"));
                for (int attempt = 0; attempt < 2; attempt++) {
                    dependencies.clear();
                    boolean flag = row.path("type").asText().equals("flag");
                    assertTrue(flag || row.path("type").asText().equals("variable"), "unsupported case type");
                    Evaluation evaluation = flag ? f.evaluateFlag(row.path("feature").asText(), context)
                        : f.evaluateVariable(row.path("variable").asText(), context);
                    fields(row.get("expected"), evaluation, label);
                    if (row.has("dependencies")) jsonEquals(row.get("dependencies"), json.valueToTree(dependencies), label);
                    if (flag) assertEquals(row.path("expected").path("enabled").asBoolean(), f.isEnabled(row.path("feature").asText(), context), label);
                    else if (row.path("expected").has("value")) jsonEquals(row.path("expected").get("value"), json.valueToTree(f.getVariable(row.path("variable").asText(), context)), label);
                    assertEquals(before, datafile.toJson(), label + " input ownership");
                    executed[attempt]++;
                }
            } finally { f.close(); }
        }
        assertArrayEquals(new int[]{fixture.path("cases").size(), fixture.path("cases").size()}, executed);
    }

    @Test void allCompositionCasesTwiceThroughBothGetterForms() throws Exception {
        JsonNode fixture = fixture("sdk-composition");
        assertEquals(2, fixture.path("version").asInt());
        assertEquals("composition-and-overrides", fixture.path("capability").asText());
        assertTrue(fixture.path("cases").isArray() && !fixture.path("cases").isEmpty());
        int[] executed = new int[2];
        for (JsonNode row : fixture.path("cases")) {
            DatafileContent datafile = DatafileContent.fromJson(fixture.get("datafile").toString());
            String before = datafile.toJson();
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile).logLevel(FeaturevisorLogLevel.FATAL)
                .stickyFeatures(map(row.get("stickyFeatures"))).stickyVariables(map(row.get("stickyVariables"))));
            try {
                String key = row.path("variable").asText(), label = row.path("name").asText();
                Map<String, Object> context = map(row.get("context"));
                for (int attempt = 0; attempt < 2; attempt++) {
                    Object a = f.getVariable(key, context);
                    jsonEquals(row.get("expectedValue"), json.valueToTree(a), label);
                    fields(row.get("expectedEvaluation"), f.evaluateVariable(key, context), label);
                    if (datafile.getVariables().get(key).getCompose() instanceof List && a instanceof Map
                            && !row.path("expectedEvaluation").path("reason").asText().equals("sticky")) assertNotSame(a, f.getVariable(key, context), label);
                    assertEquals(before, datafile.toJson(), label + " input ownership");
                    executed[attempt]++;
                }
            } finally { f.close(); }
        }
        assertArrayEquals(new int[]{fixture.path("cases").size(), fixture.path("cases").size()}, executed);
    }
}
