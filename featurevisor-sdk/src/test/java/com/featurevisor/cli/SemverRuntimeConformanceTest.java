package com.featurevisor.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import java.io.InputStream;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SemverRuntimeConformanceTest {
    @Test
    void portableConditionsRequirementsAndChecks() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/conformance/semver.json")) {
            assertNotNull(stream);
            fixture = json.readTree(stream);
        }
        assertEquals("featurevisor.semver", fixture.get("kind").asText());
        assertEquals(1, fixture.get("version").asInt());
        assertEquals(44, fixture.get("cases").size());
        List<String> operators = List.of("semverEquals", "semverNotEquals", "semverGreaterThan",
            "semverGreaterThanOrEquals", "semverLessThan", "semverLessThanOrEquals");
        for (JsonNode entry : fixture.get("cases")) {
            for (boolean reverse : new boolean[]{false, true}) {
                String a = entry.get(reverse ? "right" : "left").asText();
                String b = entry.get(reverse ? "left" : "right").asText();
                boolean valid = !entry.get("order").isNull();
                int order = entry.get("order").asInt() * (reverse ? -1 : 1);
                boolean[] outcomes = {order == 0, order != 0, order > 0, order >= 0, order < 0, order <= 0};
                for (int index = 0; index < operators.size(); index++) {
                    String operator = operators.get(index);
                    String label = entry.get("name").asText() + " reverse=" + reverse + " " + operator;
                    boolean expected = valid && outcomes[index];
                    Map<String, Object> requirement = Map.of("variable", "source", "operator", operator, "value", b);
                    Map<String, Object> traffic = Map.of("key", "all", "segments", "*", "percentage", 100000, "allocation", List.of());
                    Map<String, Object> conditionTraffic = new LinkedHashMap<>(traffic);
                    conditionTraffic.put("segments", List.of("version"));
                    Map<String, Object> variables = new LinkedHashMap<>();
                    variables.put("source", Map.of("type", "string", "defaultValue", a));
                    variables.put("other", Map.of("type", "string", "defaultValue", b));
                    for (boolean featureSource : new boolean[]{false, true}) {
                        Map<String, Object> selected = new LinkedHashMap<>(requirement);
                        if (featureSource) selected.put("feature", "sourceFeature");
                        for (boolean negated : new boolean[]{false, true}) {
                            variables.put("subject" + featureSource + negated, Map.of("type", "boolean",
                                "defaultValue", true, "disabledValue", false,
                                "conditions", List.of(negated ? Map.of("not", List.of(selected)) : selected)));
                        }
                    }
                    DatafileContent datafile = json.convertValue(Map.of(
                        "schemaVersion", "2", "revision", "semver",
                        "segments", Map.of("version", Map.of("conditions", Map.of("attribute", "version", "operator", operator, "value", b))),
                        "features", Map.of(
                            "condition", Map.of("bucketBy", "userId", "traffic", List.of(conditionTraffic)),
                            "sourceFeature", Map.of("bucketBy", "userId", "traffic", List.of(traffic),
                                "variablesSchema", Map.of("source", Map.of("type", "string", "defaultValue", a)))),
                        "variables", variables), DatafileContent.class);
                    Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                        .datafile(datafile).logLevel(FeaturevisorLogLevel.FATAL));
                    try {
                        assertEquals(expected, f.isEnabled("condition", Map.of("version", a)), label);
                        VariableChecks evaluator = new VariableChecks(reference -> VariableChecks.Resolution.value(
                            reference.containsKey("feature")
                                ? f.getVariable((String) reference.get("feature"), (String) reference.get("variable"))
                                : f.getVariable((String) reference.get("variable"))));
                        for (boolean featureSource : new boolean[]{false, true}) {
                            for (boolean negated : new boolean[]{false, true}) {
                                String subject = "subject" + featureSource + negated;
                                assertEquals(valid ? expected != negated : null, f.getVariable(subject), label);
                                if (!valid) assertEquals("error", f.evaluateVariable(subject).getReason(), label);
                            }
                            for (boolean compareTo : new boolean[]{false, true}) {
                                Map<String, Object> check = new LinkedHashMap<>(requirement);
                                if (featureSource) check.put("feature", "sourceFeature");
                                if (compareTo) {
                                    check.remove("value");
                                    check.put("compareTo", Map.of("variable", "other"));
                                }
                                for (boolean negated : new boolean[]{false, true}) {
                                    List<VariableChecks.CheckFailure> errors = evaluator.evaluateStructured(
                                        List.of(negated ? Map.of("not", List.of(check)) : check));
                                    assertEquals(valid && expected != negated, errors.isEmpty(), label);
                                    if (!valid) assertTrue(List.of("valid checks", "successful comparison").contains(errors.get(0).expected), label);
                                }
                            }
                        }
                    } finally {
                        f.close();
                    }
                }
            }
        }
    }
}
