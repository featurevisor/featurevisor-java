package com.featurevisor.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalVariablesConformanceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/conformance/sdk-v3.json")) {
            assertNotNull(stream);
            return objectMapper.readTree(stream);
        }
    }

    private Map<String, Object> map(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return Collections.emptyMap();
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private Object value(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
            ? null : objectMapper.convertValue(node, Object.class);
    }

    @Test void evaluatesGlobalVariablesAndRequiredFeaturesFromSharedFixture() throws Exception {
        JsonNode root = fixture();
        assertEquals(6, root.get("version").asInt());

        JsonNode global = root.get("globalVariables");
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(objectMapper.treeToValue(global.get("datafile"), DatafileContent.class))
            .logLevel(FeaturevisorLogLevel.FATAL));

        for (JsonNode testCase : global.get("cases")) {
            f.setStickyVariables(map(testCase.get("stickyVariables")), true);
            Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
            if (testCase.has("defaultVariableValue")) options.setDefaultVariableValue(value(testCase.get("defaultVariableValue")));
            Evaluation evaluation = f.evaluateVariable(testCase.get("key").asText(), map(testCase.get("context")), options);
            assertEquals(value(testCase.get("expectedValue")), evaluation.getVariableValue(), testCase.get("name").asText());
            assertEquals(testCase.get("expectedReason").asText(), evaluation.getReason(), testCase.get("name").asText());
            assertEquals(testCase.has("expectedOverrideIndex") ? testCase.get("expectedOverrideIndex").asInt() : null,
                evaluation.getVariableOverrideIndex(), testCase.get("name").asText());
            assertEquals(testCase.has("expectedOverrideKey") ? testCase.get("expectedOverrideKey").asText() : null,
                evaluation.getVariableOverrideKey(), testCase.get("name").asText());
            List<String> expectedPath = testCase.has("expectedOverridePath")
                ? objectMapper.convertValue(testCase.get("expectedOverridePath"), new TypeReference<List<String>>() {}) : null;
            assertEquals(expectedPath, evaluation.getVariableOverridePath(), testCase.get("name").asText());
        }

        JsonNode overload = global.get("overloadCase");
        assertEquals(value(overload.get("expectedGlobalValue")), f.getVariable(overload.get("sharedKey").asText()));
        assertEquals("hello", f.getVariableString("stringValue"));
        assertEquals(1, f.getVariableInteger("integerValue"));
        assertEquals(1.5, f.getVariableDouble("doubleValue"));
        assertEquals(true, f.getVariableBoolean("booleanValue"));
        assertEquals(List.of("one", "two"), f.<String>getVariableArray("arrayValue"));
        assertEquals(Map.of("enabled", true), f.<Map<String, Object>>getVariableObject("objectValue"));
        assertEquals(Map.of("enabled", true), f.<Map<String, Object>>getVariableJSON("jsonValue"));
        assertTrue(f.getVariableKeys(overload.get("sharedKey").asText()).contains(overload.get("featureVariableKey").asText()));
        Featurevisor featureF = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(objectMapper.treeToValue(global.get("datafile"), DatafileContent.class))
            .logLevel(FeaturevisorLogLevel.FATAL));
        Evaluation featureVariable = featureF.evaluateVariable(overload.get("sharedKey").asText(), overload.get("featureVariableKey").asText());
        assertTrue(featureF.getVariableKeys("shared").contains("owned"));
        assertEquals(value(overload.get("expectedFeatureValue")), featureVariable.getVariableValue(), featureVariable.toString());
        assertTrue(f.getVariableKeys().contains("shared"));
        ChildInstance child = f.spawn(Map.of("country", "nl"));
        assertEquals("global-value", child.getVariableString("shared"));
        assertEquals("global-value", child.evaluateVariable("shared").getVariableValue());

        JsonNode required = root.get("requiredFeatures");
        Featurevisor requiredF = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(objectMapper.treeToValue(required.get("datafile"), DatafileContent.class))
            .logLevel(FeaturevisorLogLevel.FATAL));
        for (JsonNode testCase : required.get("cases")) {
            assertEquals(testCase.get("expectedEnabled").asBoolean(),
                requiredF.isEnabled(testCase.get("feature").asText()), testCase.get("name").asText());
        }
        JsonNode variableCase = required.get("featureVariableCase");
        Evaluation evaluation = requiredF.evaluateVariable(
            variableCase.get("feature").asText(), variableCase.get("variable").asText());
        assertEquals(value(variableCase.get("expectedValue")), evaluation.getVariableValue());
        assertEquals(variableCase.get("expectedOverrideKey").asText(), evaluation.getVariableOverrideKey());
    }

    @SuppressWarnings("unchecked")
    private void assertSameKeys(Object actual, JsonNode expected) {
        List<String> actualValues = new ArrayList<>((List<String>) actual);
        List<String> expectedValues = objectMapper.convertValue(expected, new TypeReference<List<String>>() {});
        Collections.sort(actualValues);
        Collections.sort(expectedValues);
        assertEquals(expectedValues, actualValues);
    }

    @Test void reportsDirectAndDependencyAwareDatafileChanges() throws Exception {
        JsonNode global = fixture().get("globalVariables");
        JsonNode update = global.get("datafileUpdateCase");
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(objectMapper.treeToValue(update.get("initial"), DatafileContent.class))
            .logLevel(FeaturevisorLogLevel.FATAL));
        FeaturevisorEventDetails[] details = new FeaturevisorEventDetails[1];
        f.on(FeaturevisorEventName.DATAFILE_SET, value -> details[0] = value);
        f.setDatafile(objectMapper.treeToValue(update.get("merge"), DatafileContent.class));
        assertSameKeys(f.getFeatureKeys(), update.path("expectedAfterMerge").path("features"));
        assertSameKeys(f.getVariableKeys(), update.path("expectedAfterMerge").path("variables"));
        assertSameKeys(details[0].get("features"), update.path("expectedAfterMerge").path("changedFeatures"));
        assertSameKeys(details[0].get("variables"), update.path("expectedAfterMerge").path("changedVariables"));
        f.setDatafile(objectMapper.treeToValue(update.get("replacement"), DatafileContent.class), true);
        assertSameKeys(details[0].get("features"), update.path("expectedAfterReplacement").path("changedFeatures"));
        assertSameKeys(details[0].get("variables"), update.path("expectedAfterReplacement").path("changedVariables"));

        JsonNode dependency = global.get("dependencyUpdateCase");
        for (JsonNode mode : dependency.get("modes")) {
            Featurevisor instance = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(objectMapper.treeToValue(dependency.get("initial"), DatafileContent.class))
                .logLevel(FeaturevisorLogLevel.FATAL));
            FeaturevisorEventDetails[] dependencyDetails = new FeaturevisorEventDetails[1];
            instance.on(FeaturevisorEventName.DATAFILE_SET, value -> dependencyDetails[0] = value);
            instance.setDatafile(objectMapper.treeToValue(dependency.get("updated"), DatafileContent.class), mode.get("replace").asBoolean());
            assertSameKeys(dependencyDetails[0].get("features"), dependency.get("expectedChangedFeatures"));
            assertSameKeys(dependencyDetails[0].get("variables"), dependency.get("expectedChangedVariables"));
        }
    }

    @Test void explicitNullVariableValueBeatsCallerDefault() throws Exception {
        DatafileContent datafile = objectMapper.readValue("""
            {
              "schemaVersion": "2",
              "revision": "null-default",
              "segments": {},
              "features": {
                "feature": {
                  "key": "feature",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "nullable": {
                      "type": "json",
                      "defaultValue": null,
                      "useDefaultWhenDisabled": true
                    }
                  },
                  "traffic": []
                },
                "allocatedFeature": {
                  "key": "allocatedFeature",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "nullable": {
                      "type": "json",
                      "defaultValue": null
                    },
                    "missing": {
                      "type": "json"
                    }
                  },
                  "traffic": [{
                    "key": "all",
                    "segments": "*",
                    "percentage": 100000
                  }]
                }
              },
              "variables": {
                "nullable": {
                  "type": "json",
                  "defaultValue": null
                }
              }
            }
            """, DatafileContent.class);
        assertTrue(datafile.getVariables().get("nullable").hasDefaultValue());
        assertTrue(datafile.getFeatures().get("feature").getVariablesSchema().get("nullable").hasDefaultValue());
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile)
            .logLevel(FeaturevisorLogLevel.FATAL));
        Featurevisor.OverrideOptions options = new Featurevisor.OverrideOptions();
        options.setDefaultVariableValue(Map.of("fallback", true));

        Evaluation evaluation = f.evaluateVariable("nullable", Collections.emptyMap(), options);

        assertTrue(evaluation.hasVariableValue());
        assertNull(evaluation.getVariableValue());
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, evaluation.getReason());

        Evaluation featureEvaluation = f.evaluateVariable("feature", "nullable", Collections.emptyMap(), options);
        assertTrue(featureEvaluation.hasVariableValue());
        assertNull(featureEvaluation.getVariableValue());
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, featureEvaluation.getReason());

        Map<String, Object> context = Map.of("userId", "user");
        Evaluation allocatedNull = f.evaluateVariable("allocatedFeature", "nullable", context, options);
        assertTrue(allocatedNull.hasVariableValue());
        assertNull(allocatedNull.getVariableValue());

        Evaluation allocatedMissing = f.evaluateVariable("allocatedFeature", "missing", context, options);
        assertTrue(allocatedMissing.hasVariableValue());
        assertEquals(Map.of("fallback", true), allocatedMissing.getVariableValue());
    }
}
