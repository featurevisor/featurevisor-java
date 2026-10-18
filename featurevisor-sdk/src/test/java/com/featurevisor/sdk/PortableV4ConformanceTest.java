package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PortableV4ConformanceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void executesEveryPortableV4CaseTwice() throws Exception {
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/conformance/sdk-v4-cases.json")) {
            assertNotNull(stream);
            fixture = mapper.readTree(stream);
        }
        assertEquals(1, fixture.get("version").asInt());
        assertEquals(255, fixture.get("cases").size());

        for (JsonNode testCase : fixture.get("cases")) {
            JsonNode originalDatafile = testCase.get("datafile").deepCopy();
            DatafileContent datafile = mapper.treeToValue(testCase.get("datafile"), DatafileContent.class);
            JsonNode variablesBeforeEvaluation = mapper.valueToTree(datafile.getVariables());
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile)
                .logLevel(FeaturevisorLogLevel.FATAL));
            Map<String, Object> context = testCase.has("context")
                ? mapper.convertValue(testCase.get("context"), Map.class) : Collections.emptyMap();
            JsonNode expected = testCase.get("expected");
            Featurevisor.OverrideOptions options = options(testCase.path("options"));

            try {
                for (int run = 0; run < 2; run++) {
                    String label = testCase.get("name").asText() + " run " + (run + 1);
                    Object getterValue = f.getVariable("subject", context, options);
                    assertEquals(expected.get("value"), mapper.valueToTree(getterValue), label + " getter");

                    Evaluation evaluation = f.evaluateVariable("subject", context, options);
                    assertEquals(expected.get("reason").asText(), evaluation.getReason(), label + " reason");
                    assertEquals(expected.get("value"), mapper.valueToTree(evaluation.getVariableValue()), label + " value");
                    JsonNode actual = mapper.valueToTree(evaluation);
                    expected.fields().forEachRemaining(field -> {
                        String key = field.getKey().equals("value") ? "variableValue" : field.getKey();
                        assertEquals(field.getValue(), actual.get(key), label + " " + key);
                    });
                    if (expected.has("variableOverrideIndex")) {
                        assertEquals(expected.get("variableOverrideIndex").asInt(), evaluation.getVariableOverrideIndex(), label);
                    }
                    if (expected.has("variableOverrideKey")) {
                        assertEquals(expected.get("variableOverrideKey").asText(), evaluation.getVariableOverrideKey(), label);
                    }
                    if (expected.has("variableOverridePath")) {
                        assertEquals(expected.get("variableOverridePath"), mapper.valueToTree(evaluation.getVariableOverridePath()), label);
                    }
                    if (expected.has("variableOverridePaths")) {
                        assertEquals(expected.get("variableOverridePaths"), mapper.valueToTree(evaluation.getVariableOverridePaths()), label);
                    }
                }
                assertEquals(variablesBeforeEvaluation, mapper.valueToTree(datafile.getVariables()),
                    testCase.get("name").asText() + " must not mutate variable inputs");
                assertEquals(originalDatafile, testCase.get("datafile"),
                    testCase.get("name").asText() + " must not mutate its fixture node");
            } finally {
                f.close();
            }
        }
    }

    @Test
    void firstMatchRepresentationsRemainEquivalentAcrossChildrenAndDatafileUpdates() throws Exception {
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/conformance/sdk-v4-cases.json")) {
            assertNotNull(stream);
            fixture = mapper.readTree(stream);
        }
        DatafileContent precomputed = datafile(fixture, "first match precomputed parsed: child");
        DatafileContent runtime = datafile(fixture, "first match runtime parsed: child");
        Featurevisor root = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(precomputed)
            .context(Map.of("region", "eu", "country", "be", "eligible", true, "denied", true))
            .logLevel(FeaturevisorLogLevel.FATAL));
        ChildInstance blockedChild = root.spawn(Map.of("country", "nl"));
        ChildInstance allowedChild = root.spawn(Map.of("country", "nl"),
            new Featurevisor.SpawnOptions().stickyVariables(Map.of("permission", true)));
        Map<String, Object> parentValue = Map.of(
            "stage", "parent",
            "rows", List.of(Map.of("id", 1, "tags", List.of("parent"))),
            "flags", List.of("a"));
        Map<String, Object> childValue = Map.of(
            "stage", "parent",
            "rows", List.of(
                Map.of("id", 0, "tags", List.of()),
                Map.of("id", 1, "tags", List.of("parent", "child"))),
            "flags", List.of("a"));

        assertValues(root, blockedChild, allowedChild, parentValue, childValue, false);
        root.setDatafile(runtime, true);
        assertValues(root, blockedChild, allowedChild, parentValue, childValue, true);
        root.setDatafile(precomputed);
        assertValues(root, blockedChild, allowedChild, parentValue, childValue, false);
    }

    private DatafileContent datafile(JsonNode fixture, String name) throws Exception {
        for (JsonNode testCase : fixture.get("cases")) {
            if (name.equals(testCase.get("name").asText())) {
                return mapper.treeToValue(testCase.get("datafile"), DatafileContent.class);
            }
        }
        throw new IllegalArgumentException("Missing fixture case: " + name);
    }

    private void assertValues(
        Featurevisor root,
        ChildInstance blockedChild,
        ChildInstance allowedChild,
        Map<String, Object> parentValue,
        Map<String, Object> childValue,
        boolean program
    ) {
        for (int run = 0; run < 2; run++) {
            assertEquals(parentValue, root.getVariable("subject"));
            assertEquals(parentValue, blockedChild.getVariable("subject"));
            assertEquals(childValue, allowedChild.getVariable("subject"));
        }
        assertEquals(
            program ? List.of(List.of("parent"), List.of("parent", "child")) : null,
            allowedChild.evaluateVariable("subject").getVariableOverridePaths());
    }

    private Featurevisor.OverrideOptions options(JsonNode node) {
        Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
        if (node.has("defaultVariationValue")) {
            options.defaultVariationValue(node.get("defaultVariationValue").asText());
        }
        if (node.has("defaultVariableValue")) {
            options.defaultVariableValue(mapper.convertValue(node.get("defaultVariableValue"), Object.class));
        }
        return options;
    }
}
