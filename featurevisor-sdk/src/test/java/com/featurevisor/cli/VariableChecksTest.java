package com.featurevisor.cli;

import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Evaluation;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorDiagnostic;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VariableChecksTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void executesEveryPortableCaseWithAFreshSdk() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("conformance/test-checks.json");
        assertNotNull(stream);
        Map<String, Object> fixture = json.readValue(stream, new TypeReference<Map<String, Object>>() {});
        assertEquals(3, ((Number) fixture.get("version")).intValue());
        assertEquals(new HashSet<>(Arrays.asList("version", "description", "datafile", "cases")), fixture.keySet());
        @SuppressWarnings("unchecked") List<Map<String, Object>> cases = (List<Map<String, Object>>) fixture.get("cases");
        assertEquals(255, cases.size());
        DatafileContent datafile = json.convertValue(fixture.get("datafile"), DatafileContent.class);

        int executedAnyCases = 0;
        for (Map<String, Object> testCase : cases) {
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile).logLevel(FeaturevisorLogLevel.FATAL).onDiagnostic(diagnostics::add));
            try {
                String owner = testCase.get("owner") instanceof String ? (String) testCase.get("owner") : null;
                List<String> failures = new VariableChecks(reference -> {
                    String feature = reference.get("feature") instanceof String ? (String) reference.get("feature") : null;
                    String variable = reference.get("variable") instanceof String ? (String) reference.get("variable") : owner;
                    if (variable == null) return VariableChecks.Resolution.error("variable is required");
                    if (!reference.containsKey("variable") && owner != null) reference.put("variable", owner);
                    diagnostics.clear();
                    Evaluation evaluation;
                    Object value;
                    if (feature == null) {
                        if (datafile.getVariables() == null || !datafile.getVariables().containsKey(variable)) {
                            return VariableChecks.Resolution.error("missing global " + variable);
                        }
                        evaluation = f.evaluateVariable(variable);
                        if ("error".equals(evaluation.getReason()) || evaluation.getError() != null) {
                            return VariableChecks.Resolution.error("evaluation failed");
                        }
                        value = f.getVariable(variable);
                    } else {
                        if (datafile.getFeature(feature) == null || datafile.getFeature(feature).getVariablesSchema() == null
                                || !datafile.getFeature(feature).getVariablesSchema().containsKey(variable)) {
                            return VariableChecks.Resolution.error("missing feature variable " + feature + "." + variable);
                        }
                        evaluation = f.evaluateVariable(feature, variable);
                        if ("error".equals(evaluation.getReason()) || evaluation.getError() != null) {
                            return VariableChecks.Resolution.error("evaluation failed");
                        }
                        value = f.getVariable(feature, variable);
                    }
                    for (FeaturevisorDiagnostic diagnostic : diagnostics) {
                        if (diagnostic.getLevel() == FeaturevisorLogLevel.ERROR) {
                            return VariableChecks.Resolution.error("getter failed: " + diagnostic.getCode());
                        }
                    }
                    return VariableChecks.Resolution.value(value);
                }, (feature, getter) -> {
                    if (datafile.getFeature(feature) == null) {
                        return VariableChecks.Resolution.error("missing feature " + feature);
                    }
                    return VariableChecks.Resolution.value(
                        getter.equals("enabled") ? f.isEnabled(feature) : f.getVariation(feature)
                    );
                }).evaluate(testCase.get("checks"));
                boolean expectedPassed = Boolean.TRUE.equals(testCase.get("passed"));
                boolean expectedError = Boolean.TRUE.equals(testCase.get("error"));
                assertEquals(expectedPassed, failures.isEmpty(), testCase.get("name") + ": " + failures);
                assertEquals(expectedError, failures.stream().anyMatch(value -> value.contains(" error:")),
                    testCase.get("name") + ": " + failures);
                if (((String) testCase.get("name")).toLowerCase(Locale.ROOT).contains("includesany")) executedAnyCases++;
            } finally {
                f.close();
            }
        }
        assertEquals(36, executedAnyCases);
    }

    @Test
    void nativeRunnerDispatchesFiltersAndRunsScenarioChildren() throws Exception {
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--segments") || command.contains("--targets")) return "[]";
            if (command.contains(" build ")) return datafileJson(false);
            return executionProtocol("[{\"scenario\":\"ordered values\",\"assertions\":[{"
                + "\"description\":\"parent and child\","
                + "\"checks\":[{\"variable\":\"left\",\"operator\":\"greaterThan\",\"compareTo\":{\"variable\":\"right\"}}],"
                + "\"children\":[{\"stickyVariables\":{\"left\":4,\"right\":4},\"checks\":[{\"variable\":\"left\",\"operator\":\"equals\",\"compareTo\":{\"variable\":\"right\"}}]}]}]}]");
        });
        int code = new CommandLine(cli).execute("test", "--entityType=scenario", "--set=staging");
        assertEquals(0, code);
        assertTrue(calls.stream().anyMatch(command -> command.contains("--entityType=scenario") && command.contains("--set=staging")));
        assertTrue(calls.stream().filter(command -> !command.contains(" config ")).allMatch(command -> command.contains("--set=staging")));
        assertTrue(calls.stream().filter(command -> command.contains(" config ")).noneMatch(command -> command.contains("--set=")));
    }

    @Test
    void featureChecksUseOnlyRequestedGettersAndReportBothActualValues() throws Exception {
        Object checks = json.readValue("[{\"feature\":\"disabledCheckout\",\"variation\":\"control\"},"
            + "{\"feature\":\"experiment\",\"enabled\":true,\"variation\":\"control\"}]", Object.class);
        List<String> calls = new ArrayList<>();
        List<String> failures = new VariableChecks(
            reference -> { throw new AssertionError("variable resolver must not run"); },
            (feature, getter) -> {
                calls.add(feature + ":" + getter);
                return VariableChecks.Resolution.value(
                    getter.equals("enabled")
                        ? false
                        : feature.equals("experiment") ? "treatment" : "control"
                );
            }
        ).evaluate(checks);

        assertEquals(Arrays.asList(
            "disabledCheckout:variation", "experiment:enabled", "experiment:variation"
        ), calls);
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("expected enabled true, actual false"));
        assertTrue(failures.get(0).contains("expected variation \"control\", actual \"treatment\""));
    }

    @Test
    void featureGetterErrorsCannotBeInvertedOrHiddenAndStopBeforeVariation() throws Exception {
        Object checks = json.readValue("[{\"not\":[{\"feature\":\"experiment\",\"enabled\":true,\"variation\":\"control\"}]},"
            + "{\"or\":[{\"feature\":\"experiment\",\"enabled\":true,\"variation\":\"control\"},"
            + "{\"feature\":\"checkout\",\"enabled\":true}]}]", Object.class);
        List<String> calls = new ArrayList<>();
        List<String> failures = new VariableChecks(
            reference -> { throw new AssertionError("variable resolver must not run"); },
            (feature, getter) -> {
                calls.add(feature + ":" + getter);
                return VariableChecks.Resolution.error(
                    getter.equals("enabled") ? "boom" : "evaluation_error"
                );
            }
        ).evaluate(checks);

        assertEquals(Arrays.asList(
            "experiment:enabled", "experiment:enabled"
        ), calls);
        assertEquals(2, failures.size());
        assertTrue(failures.stream().allMatch(value -> value.contains("enabled getter failed: boom")));
        assertTrue(failures.stream().noneMatch(value -> value.contains("variation getter failed")));
    }

    @Test
    void segmentChecksValidateNestEvaluateAndReportEvidence() throws Exception {
        Map<String, Boolean> matches = new HashMap<>();
        matches.put("dutch", true);
        matches.put("adult", true);
        matches.put("all", true);
        VariableChecks checks = new VariableChecks(
            reference -> { throw new AssertionError("variable resolver must not run"); },
            (feature, getter) -> { throw new AssertionError("feature resolver must not run"); },
            segment -> matches.containsKey(segment)
                ? VariableChecks.Resolution.value(matches.get(segment))
                : VariableChecks.Resolution.error("segment \"" + segment + "\" definition is missing"),
            segment -> matches.containsKey(segment)
                ? null
                : "segment \"" + segment + "\" definition is missing"
        );
        Object passing = json.readValue("[{\"segment\":\"all\",\"matches\":true},"
            + "{\"segment\":\"dutch\",\"matches\":true},"
            + "{\"and\":[{\"segment\":\"adult\",\"matches\":true},"
            + "{\"not\":[{\"segment\":\"dutch\",\"matches\":false}]}]},"
            + "{\"or\":[{\"segment\":\"dutch\",\"matches\":false},"
            + "{\"segment\":\"adult\",\"matches\":true}]}]", Object.class);
        assertTrue(checks.evaluateStructured(passing).isEmpty());

        Object failing = json.readValue("[{\"key\":\"audience\",\"description\":\"Audience is excluded\","
            + "\"segment\":\"dutch\",\"matches\":false}]", Object.class);
        List<VariableChecks.CheckFailure> failures = checks.evaluateStructured(failing);
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).message.contains("segment \"dutch\""));
        @SuppressWarnings("unchecked") Map<String, Object> evidence =
            ((List<Map<String, Object>>) failures.get(0).details.get("evaluated")).get(0);
        assertEquals(Collections.singletonMap("segment", "dutch"), evidence.get("reference"));
        assertEquals("matches", evidence.get("property"));
        assertEquals("equals", evidence.get("operator"));
        assertEquals(false, evidence.get("expected"));
        assertEquals(true, evidence.get("actual"));

        Object missing = json.readValue("[{\"or\":[{\"segment\":\"dutch\",\"matches\":true},"
            + "{\"segment\":\"missing\",\"matches\":false}]}]", Object.class);
        List<VariableChecks.CheckFailure> missingFailures = checks.evaluateStructured(missing);
        assertEquals(1, missingFailures.size());
        assertTrue(missingFailures.get(0).message.contains("segment \"missing\""));

        List<String> invalid = Arrays.asList(
            "[{\"segment\":\"\",\"matches\":true}]",
            "[{\"segment\":\"dutch\"}]",
            "[{\"segment\":\"dutch\",\"matches\":\"yes\"}]",
            "[{\"matches\":false}]",
            "[{\"segment\":\"dutch\",\"matches\":true,\"operator\":\"equals\",\"value\":true}]",
            "[{\"segment\":\"dutch\",\"matches\":true,\"feature\":\"checkout\"}]",
            "[{\"segment\":\"dutch\",\"matches\":true,\"target\":\"other\"}]"
        );
        for (String raw : invalid) {
            assertFalse(checks.evaluateStructured(json.readValue(raw, Object.class)).isEmpty(), raw);
        }
    }

    @Test
    void segmentValidationDoesNotResolveSkippedBranchesOrDuplicateReachedLeaves() throws Exception {
        Map<String, Integer> validations = new HashMap<>();
        Map<String, Integer> resolutions = new HashMap<>();
        Set<String> available = new HashSet<>(Arrays.asList("first", "skipped"));
        VariableChecks checks = new VariableChecks(
            reference -> { throw new AssertionError("variable resolver must not run"); },
            (feature, getter) -> { throw new AssertionError("feature resolver must not run"); },
            segment -> {
                resolutions.merge(segment, 1, Integer::sum);
                return VariableChecks.Resolution.value(true);
            },
            segment -> {
                validations.merge(segment, 1, Integer::sum);
                return available.contains(segment)
                    ? null
                    : "segment \"" + segment + "\" definition is missing";
            }
        );
        Object passing = json.readValue("[{\"or\":[{\"segment\":\"first\",\"matches\":true},"
            + "{\"segment\":\"skipped\",\"matches\":true}]}]", Object.class);

        assertTrue(checks.evaluateStructured(passing).isEmpty());
        assertEquals(1, validations.get("first"));
        assertEquals(1, validations.get("skipped"));
        assertEquals(1, resolutions.get("first"));
        assertFalse(resolutions.containsKey("skipped"));
    }

    @Test
    void nativeRunnerUsesAuthoredSegmentsRootAndMergedChildContextOutsideTargetDatafile() throws Exception {
        Map<String, Object> definitions = new LinkedHashMap<>();
        definitions.put("all", Collections.singletonMap("conditions", "*"));
        definitions.put("dutch", Collections.singletonMap("conditions", Arrays.asList(
            linkedMap("attribute", "country", "operator", "equals", "value", "nl")
        )));
        definitions.put("dutch-pro", Collections.singletonMap("conditions", Arrays.asList(
            linkedMap("attribute", "country", "operator", "equals", "value", "nl"),
            linkedMap("attribute", "plan", "operator", "equals", "value", "pro")
        )));
        Map<String, Object> root = linkedMap(
            "context", Collections.singletonMap("country", "nl"),
            "target", "thin",
            "at", 0.1,
            "checks", Arrays.asList(
                linkedMap("segment", "all", "matches", true),
                linkedMap("segment", "dutch", "matches", true)
            ),
            "children", Arrays.asList(linkedMap(
                "context", Collections.singletonMap("plan", "pro"),
                "stickyVariables", Collections.singletonMap("subject", "ignored"),
                "checks", Arrays.asList(linkedMap("segment", "dutch-pro", "matches", true))
            ))
        );
        List<Map<String, Object>> tests = Arrays.asList(
            linkedMap("scenario", "segment-context", "_segments", definitions, "assertions", Arrays.asList(root)),
            linkedMap("variable", "subject", "_segments", definitions, "assertions", Arrays.asList(linkedMap(
                "context", Collections.singletonMap("country", "nl"),
                "target", "thin",
                "expectedValue", "ready",
                "checks", Arrays.asList(linkedMap("segment", "dutch", "matches", true))
            )))
        );
        String testsJson = executionProtocol(json.writeValueAsString(tests));
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets")) return "[{\"key\":\"thin\"}]";
            if (command.contains("--segments")) return "[]";
            if (command.contains(" build ")) {
                return "{\"schemaVersion\":\"2\",\"revision\":\"target\",\"segments\":{},\"features\":{},"
                    + "\"variables\":{\"subject\":{\"type\":\"string\",\"defaultValue\":\"ready\"}}}";
            }
            return testsJson;
        });
        assertEquals(0, new CommandLine(cli).execute("test", "--target=thin", "--onlyFailures"));
        assertTrue(calls.stream().anyMatch(command -> command.contains(" build ") && command.contains("--target=thin")));
    }

    @Test
    void structuredFailuresPreserveMissingStateAndAuthoredMetadata() throws Exception {
        Object checks = json.readValue("[{\"key\":\"missing-value\",\"description\":\"Must be null\","
            + "\"variable\":\"settings\",\"path\":\"absent\",\"operator\":\"isNull\"}]", Object.class);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("literal", "<missing>");
        List<VariableChecks.CheckFailure> failures = new VariableChecks(
            reference -> VariableChecks.Resolution.value(settings)
        ).evaluateStructured(checks);

        assertEquals(1, failures.size());
        VariableChecks.CheckFailure failure = failures.get(0);
        assertEquals(Arrays.asList("checks", 0), failure.path);
        assertEquals("missing-value", failure.details.get("checkKey"));
        assertEquals("Must be null", failure.details.get("checkDescription"));
        @SuppressWarnings("unchecked")
        Map<String, Object> leaf = ((List<Map<String, Object>>) failure.details.get("evaluated")).get(0);
        assertEquals(true, leaf.get("actualMissing"));
        assertFalse(leaf.containsKey("expectedMissing"));
        assertEquals(Collections.singletonMap("$type", "undefined"), leaf.get("actual"));

        Object literalCheck = json.readValue("[{\"variable\":\"settings\",\"path\":\"literal\","
            + "\"operator\":\"equals\",\"value\":\"different\"}]", Object.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> literalLeaf = ((List<Map<String, Object>>) new VariableChecks(
            reference -> VariableChecks.Resolution.value(settings)
        ).evaluateStructured(literalCheck).get(0).details.get("evaluated")).get(0);
        assertEquals("<missing>", literalLeaf.get("actual"));
        assertFalse(literalLeaf.containsKey("actualMissing"));

        Object missingExpectedCheck = json.readValue("[{\"variable\":\"settings\",\"path\":\"literal\","
            + "\"operator\":\"equals\",\"compareTo\":{\"variable\":\"settings\",\"path\":\"absent\"}}]",
            Object.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> missingExpectedLeaf = ((List<Map<String, Object>>) new VariableChecks(
            reference -> VariableChecks.Resolution.value(settings)
        ).evaluateStructured(missingExpectedCheck).get(0).details.get("evaluated")).get(0);
        assertEquals(true, missingExpectedLeaf.get("expectedMissing"));
        assertFalse(missingExpectedLeaf.containsKey("actualMissing"));
    }

    @Test
    void emptyChecksValidateAndCompareSupportedContainers() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("emptyString", "");
        values.put("whitespace", " \t");
        values.put("emptyArray", List.of());
        values.put("arrayWithNull", Arrays.asList((Object) null));
        values.put("emptyObject", Map.of());
        values.put("objectWithNull", Collections.singletonMap("value", null));
        values.put("null", null);
        values.put("number", 0);
        values.put("boolean", false);
        values.put("nested", Map.of("items", List.of()));
        VariableChecks checks = new VariableChecks(reference -> VariableChecks.Resolution.value(values));

        Object passing = json.readValue("["
            + "{\"variable\":\"subject\",\"path\":\"emptyString\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"path\":\"whitespace\",\"operator\":\"notEmpty\"},"
            + "{\"variable\":\"subject\",\"path\":\"emptyArray\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"path\":\"arrayWithNull\",\"operator\":\"notEmpty\"},"
            + "{\"variable\":\"subject\",\"path\":\"emptyObject\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"path\":\"objectWithNull\",\"operator\":\"notEmpty\"},"
            + "{\"variable\":\"subject\",\"path\":\"nested.items\",\"operator\":\"empty\"}]", Object.class);
        assertTrue(checks.evaluateStructured(passing).isEmpty());

        Object nullAndMissing = json.readValue("["
            + "{\"variable\":\"subject\",\"path\":\"null\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"path\":\"null\",\"operator\":\"notEmpty\"},"
            + "{\"variable\":\"subject\",\"path\":\"missing\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"path\":\"missing\",\"operator\":\"notEmpty\"}]", Object.class);
        List<VariableChecks.CheckFailure> falseResults = checks.evaluateStructured(nullAndMissing);
        assertEquals(4, falseResults.size());
        assertTrue(falseResults.stream().noneMatch(failure -> failure.message.contains(" error:")));

        for (String path : List.of("number", "boolean")) {
            for (String operator : List.of("empty", "notEmpty")) {
                Object incompatible = json.readValue("[{\"variable\":\"subject\",\"path\":\"" + path
                    + "\",\"operator\":\"" + operator + "\"}]", Object.class);
                List<VariableChecks.CheckFailure> failures = checks.evaluateStructured(incompatible);
                assertEquals(1, failures.size());
                assertTrue(failures.get(0).message.contains(" error:"), failures.get(0).message);
            }
        }
    }

    @Test
    void emptyCheckErrorsCannotBeSkippedOrInverted() throws Exception {
        VariableChecks values = new VariableChecks(reference -> VariableChecks.Resolution.value(Map.of()));
        for (String invalid : List.of(
                "[{\"or\":[{\"variable\":\"subject\",\"operator\":\"empty\"},{\"variable\":\"subject\",\"operator\":\"empty\",\"value\":1}]}]",
                "[{\"variable\":\"subject\",\"operator\":\"empty\",\"compareTo\":{\"variable\":\"subject\"}}]",
                "[{\"variable\":\"subject\",\"operator\":\"notEmpty\",\"regexFlags\":\"i\"}]")) {
            List<VariableChecks.CheckFailure> failures = values.evaluateStructured(json.readValue(invalid, Object.class));
            assertEquals(1, failures.size());
            assertTrue(failures.get(0).message.contains(" error:"), failures.get(0).message);
        }

        VariableChecks scalar = new VariableChecks(reference -> VariableChecks.Resolution.value(0));
        Object scalarOr = json.readValue("[{\"or\":[{\"variable\":\"subject\",\"operator\":\"empty\"},"
            + "{\"variable\":\"subject\",\"operator\":\"equals\",\"value\":0}]}]", Object.class);
        assertTrue(scalar.evaluateStructured(scalarOr).get(0).message.contains(" error:"));
        Object scalarNot = json.readValue("[{\"not\":[{\"variable\":\"subject\",\"operator\":\"notEmpty\"}]}]", Object.class);
        assertTrue(scalar.evaluateStructured(scalarNot).get(0).message.contains(" error:"));

        VariableChecks getterError = new VariableChecks(reference -> VariableChecks.Resolution.error("getter failed"));
        List<VariableChecks.CheckFailure> getterFailures = getterError.evaluateStructured(scalarNot);
        assertEquals(1, getterFailures.size());
        assertTrue(getterFailures.get(0).message.contains("getter failed"));
        assertTrue(getterFailures.get(0).message.contains(" error:"));
    }

    @Test
    void checkKeysAreUniqueOnlyAmongSiblingsAndDoNotChangeMatching() throws Exception {
        Object mixed = json.readValue("[{\"key\":\"outer\",\"and\":["
            + "{\"key\":\"nested\",\"variable\":\"value\",\"operator\":\"equals\",\"value\":3},"
            + "{\"variable\":\"value\",\"operator\":\"equals\",\"value\":3}]},"
            + "{\"key\":\"nested\",\"variable\":\"value\",\"operator\":\"equals\",\"value\":3}]",
            Object.class);
        assertTrue(new VariableChecks(reference -> VariableChecks.Resolution.value(3))
            .evaluateStructured(mixed).isEmpty());

        Object duplicate = json.readValue("[{\"key\":\"same\",\"variable\":\"value\","
            + "\"operator\":\"equals\",\"value\":3},{\"key\":\"same\",\"variable\":\"value\","
            + "\"operator\":\"equals\",\"value\":3}]", Object.class);
        List<VariableChecks.CheckFailure> failures = new VariableChecks(
            reference -> VariableChecks.Resolution.value(3)
        ).evaluateStructured(duplicate);
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).message.contains("duplicate sibling key"));
    }

    @Test
    void nativeRunnerReturnsFailureForPredicateMalformedSpecAndGetterDiagnostic() {
        assertEquals(1, runnerCode("[{\"scenario\":\"failure\",\"assertions\":[{\"checks\":[{\"variable\":\"left\",\"operator\":\"equals\",\"value\":99}]}]}]", false));
        assertEquals(1, runnerCode("[{\"scenario\":\"malformed\",\"assertions\":[]}]", false));
        assertEquals(1, runnerCode("[{\"scenario\":\"hybrid\",\"variable\":\"left\",\"assertions\":[{\"checks\":[{\"variable\":\"left\",\"operator\":\"equals\",\"value\":3}]}]}]", false));
        assertEquals(1, runnerCode("[{\"scenario\":\"bad json\",\"assertions\":[{\"checks\":[{\"variable\":\"broken\",\"operator\":\"isNull\"}]}]}]", true));
        assertEquals(1, runnerCode("[{\"scenario\":\"feature diagnostic\",\"assertions\":[{\"checks\":[{\"feature\":\"diagnostic\",\"enabled\":true,\"variation\":\"control\"}]}]}]", true));
    }

    private int runnerCode(String tests, boolean malformedJson) {
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--segments") || command.contains("--targets")) return "[]";
            if (command.contains(" build ")) return datafileJson(malformedJson);
            return executionProtocol(tests);
        });
        return new CommandLine(cli).execute("test", "--onlyFailures");
    }

    private String datafileJson(boolean malformedJson) {
        return "{\"schemaVersion\":\"2\",\"revision\":\"checks\",\"segments\":{},\"features\":"
            + (malformedJson ? "{\"diagnostic\":{\"bucketBy\":[\"userId\"],\"conditions\":[{\"variable\":\"broken\",\"operator\":\"isNull\"}],\"variations\":[{\"value\":\"control\"}],\"traffic\":[{\"key\":\"all\",\"segments\":\"*\",\"percentage\":100000,\"allocation\":[{\"variation\":\"control\",\"range\":[0,100000]}]}]}}" : "{}")
            + ",\"variables\":{"
            + "\"left\":{\"type\":\"integer\",\"defaultValue\":3},"
            + "\"right\":{\"type\":\"integer\",\"defaultValue\":2}"
            + (malformedJson ? ",\"broken\":{\"type\":\"json\",\"defaultValue\":\"{\"}" : "")
            + "}}";
    }

    private String executionProtocol(String tests) {
        return "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":"
            + tests + "}";
    }

    @SuppressWarnings("unchecked")
    private static <T extends Map<String, Object>> T linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return (T) result;
    }
}
