package com.featurevisor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CLIReportingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void delegatedFiltersRetainRawValuesAndProjectDirectory(@TempDir Path directory) throws Exception {
        Path project = Files.createDirectory(directory.resolve("project's spaced path"));
        String pattern = "O'Reilly; $(echo unsafe) 東京";
        var calls = new ArrayList<List<String>>();
        CLI cli = new CLI() {
            @Override
            String executeCommandInDirectory(String workingDirectory, String command, List<String> arguments) throws IOException {
                assertEquals(project.toString(), workingDirectory);
                assertFalse(command.contains(pattern));
                assertFalse(command.contains("team's"));
                if (command.contains(" config ")) return "{\"environments\":false}";
                assertTrue(arguments.contains("--set=web team's set"));
                if (command.contains("--tests")) {
                    calls.add(new ArrayList<>(arguments));
                    return executionProtocol("[]");
                }
                if (arguments.contains("build")) return "{\"schemaVersion\":\"2\",\"revision\":\"test\",\"features\":{},\"segments\":{}}";
                return "[]";
            }
        };
        Execution execution = execute(cli, "test", "--json", "--root-directory-path", project.toString(),
            "--key-pattern", pattern, "--assertion-pattern=country: nl", "--set=web team's set");
        assertEquals(0, execution.code, execution.stdout + execution.stderr);
        assertEquals(List.of(List.of("--keyPattern=" + pattern, "--assertionPattern=country: nl", "--set=web team's set")), calls);
    }

    @Test
    void invalidOptionsFailBeforeDelegationAndKeepStdoutClean() throws Exception {
        for (String command : List.of("test", "benchmark", "assess-distribution")) {
            for (String[] options : new String[][]{
                {"--unknown"}, {"extra"}, {"--n=0"}, {"-n", "-2"}, {"--n=1.5"},
                {"--inflate=0"}, {"--entity-type=unknown"}, {"--context=[]"},
                {"--context={"}, {"--context=null"}, {"--context={} trailing"}, {"--target="}, {"--populate-uuid="},
                {"--root-directory-path="}, {"--only-failures=invalid"}, {"--target"},
                {"--populate-uuid"}, {"--key-pattern"}}) {
                CLI cli = new CLI((directory, delegated) -> { throw new AssertionError(delegated); });
                var args = new ArrayList<String>(List.of(command, "--json"));
                args.addAll(List.of(options));
                Execution execution = execute(cli, args.toArray(new String[0]));
                assertNotEquals(0, execution.code, args.toString());
                assertEquals("", execution.stdout, args.toString());
                assertFalse(execution.stderr.isEmpty(), args.toString());
            }
        }
        Execution unknown = execute(new CLI((directory, delegated) -> { throw new AssertionError(delegated); }), "unknown");
        assertNotEquals(0, unknown.code);
        assertEquals("", unknown.stdout);
        for (String command : List.of("benchmark", "assess-distribution")) {
            Execution missing = execute(new CLI((directory, delegated) -> { throw new AssertionError(delegated); }), command);
            assertNotEquals(0, missing.code);
            assertEquals("", missing.stdout);
            assertFalse(missing.stderr.isEmpty());
            Execution conflicting = execute(new CLI((directory, delegated) -> { throw new AssertionError(delegated); }),
                command, "--feature=ready", "--variation", "--variable=text");
            assertNotEquals(0, conflicting.code);
            assertEquals("", conflicting.stdout);
        }
    }

    @Test
    void helpListsAliasesWithoutDelegation() throws Exception {
        for (String[] args : new String[][]{{"--help"}, {"test", "--help"}, {"benchmark", "-h"}, {"assess-distribution", "--help"}}) {
            Execution execution = execute(new CLI((directory, delegated) -> { throw new AssertionError(delegated); }), args);
            assertEquals(0, execution.code);
            for (String option : List.of("--root-directory-path", "--key-pattern", "--populate-uuid", "--show-datafile")) {
                assertTrue(execution.stdout.contains(option), execution.stdout);
            }
        }
    }

    @Test
    void invalidSemverSegmentsEmitOneJsonDocumentEvenWhenVerbose(@TempDir Path project) throws Exception {
        var condition = java.util.Map.of("attribute", "version", "operator", "semverEquals", "value", "1.0.0");
        var tests = json.createArrayNode();
        var segment = tests.addObject().put("segment", "stableVersion").putArray("assertions");
        var scenario = tests.addObject().put("scenario", "segment checks");
        scenario.set("_segments", json.valueToTree(java.util.Map.of("stableVersion", java.util.Map.of("conditions", condition))));
        var checks = scenario.putArray("assertions");
        for (String version : List.of("١.0.0", "1.0.0\n", "1.0.0\n2.0.0", "1.0.0-K")) {
            var context = json.valueToTree(java.util.Map.of("version", version));
            segment.addObject().put("expectedToMatch", false).set("context", context);
            var assertion = checks.addObject();
            assertion.set("context", context);
            assertion.putArray("checks").addObject().put("segment", "stableVersion").put("matches", false);
        }
        for (boolean verbose : new boolean[]{false, true}) {
            CLI cli = new CLI((directory, command) -> {
                if (command.contains(" config ")) return "{\"environments\":false}";
                if (command.contains("--targets")) return "[]";
                if (command.contains("--segments")) return json.writeValueAsString(List.of(
                    java.util.Map.of("key", "stableVersion", "conditions", condition)));
                if (command.contains(" build ")) return "{\"schemaVersion\":\"2\",\"revision\":\"semver\",\"segments\":{},\"features\":{}}";
                return executionProtocol(tests.toString());
            });
            List<String> args = new ArrayList<>(List.of("test", "--json", "--projectDirectoryPath=" + project));
            if (verbose) args.add("--verbose");
            Execution execution = execute(cli, args.toArray(new String[0]));
            assertEquals(0, execution.code, execution.stderr);
            try (var parser = json.getFactory().createParser(execution.stdout)) {
                JsonNode report = json.readTree(parser);
                assertTrue(report.get("passed").asBoolean());
                assertEquals(8, report.at("/summary/assertions/passed").asInt());
                assertNull(parser.nextToken(), "stdout must contain exactly one JSON document");
            }
            assertFalse(execution.stdout.contains("condition_match_error"));
        }
    }

    @Test
    void semverErrorsRemainStructuredThroughCliReportsAndNegation(@TempDir Path project) throws Exception {
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return "{\"schemaVersion\":\"2\",\"revision\":\"semver\",\"segments\":{},\"features\":{},"
                + "\"variables\":{\"valid\":{\"type\":\"string\",\"defaultValue\":\"1.0.0\"},"
                + "\"invalid\":{\"type\":\"string\",\"defaultValue\":\"invalid\"}}}";
            return executionProtocol("[{\"scenario\":\"semver errors\",\"assertions\":["
                + "{\"checks\":[{\"not\":[{\"variable\":\"valid\",\"operator\":\"semverEquals\",\"value\":\"invalid\"}]}]},"
                + "{\"checks\":[{\"not\":[{\"variable\":\"invalid\",\"operator\":\"semverEquals\",\"compareTo\":{\"variable\":\"valid\"}}]}]},"
                + "{\"children\":[{\"checks\":[{\"not\":[{\"variable\":\"invalid\",\"operator\":\"semverEquals\",\"value\":\"1.0.0\"}]}]}]}]}]");
        });
        Execution execution = execute(cli, "test", "--json", "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode cases = json.readTree(execution.stdout).at("/results/0/cases");
        assertEquals(3, cases.size());
        for (int index = 0; index < cases.size(); index++) {
            assertFalse(cases.get(index).get("passed").asBoolean());
            JsonNode errors = cases.get(index).get("errors");
            assertEquals(1, errors.size());
            JsonNode error = errors.get(0);
            assertEquals("check", error.get("type").asText());
            assertEquals(index == 0 ? "valid checks" : "successful comparison", error.get("expected").asText());
            assertTrue(error.get("actual").isTextual());
            assertTrue(error.get("actual").asText().contains("invalid"));
            assertEquals(json.readTree(index == 2 ? "[\"children\",0,\"checks\",0]" : "[\"checks\",0]"), error.get("path"));
            if (index == 2) assertEquals(0, error.at("/details/childIndex").asInt(-1));
        }
    }

    @Test
    void ordinaryFailuresPreserveAuthoredPathsValuesAndChildren(@TempDir Path project) throws Exception {
        var feature = json.readTree("{\"expectedToBeEnabled\":false,\"expectedVariation\":\"missing\","
            + "\"expectedVariables\":{\"text\":\"wrong\"},\"expectedEvaluations\":{\"flag\":{\"enabled\":false},"
            + "\"variation\":{\"featureKey\":\"wrong\"},\"variables\":{\"text\":{\"variableValue\":\"wrong\"}}}}");
        var variable = json.readTree("{\"expectedValue\":\"wrong\",\"expectedEvaluation\":{\"variableValue\":\"wrong\"}}");
        var tests = json.createArrayNode();
        for (var entry : List.of(java.util.Map.entry("feature", feature), java.util.Map.entry("variable", variable))) {
            var assertion = (com.fasterxml.jackson.databind.node.ObjectNode) entry.getValue().deepCopy();
            assertion.putArray("children").add(entry.getValue());
            assertion.putObject("_case").put("assertionIndex", 7);
            tests.addObject().put(entry.getKey(), entry.getKey().equals("feature") ? "flag" : "text")
                .putArray("assertions").add(assertion);
        }
        tests.addObject().put("segment", "all").putArray("assertions").addObject()
            .put("expectedToMatch", false).putObject("_case").put("assertionIndex", 7);
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets")) return "[]";
            if (command.contains("--segments")) return "[{\"key\":\"all\",\"conditions\":\"*\"}]";
            if (command.contains(" build ")) return "{\"schemaVersion\":\"2\",\"revision\":\"failures\",\"segments\":{},"
                + "\"features\":{\"flag\":{\"bucketBy\":\"userId\",\"traffic\":[{\"key\":\"all\",\"segments\":\"*\",\"percentage\":100000,\"allocation\":[]}],"
                + "\"variablesSchema\":{\"text\":{\"type\":\"string\",\"defaultValue\":\"actual\"}}}},"
                + "\"variables\":{\"text\":{\"type\":\"string\",\"defaultValue\":\"actual\"}}}";
            return executionProtocol(tests.toString());
        });
        Execution execution = execute(cli, "test", "--json", "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode results = json.readTree(execution.stdout).get("results");
        JsonNode expected = json.readTree("["
            + "[[\"flag\",[\"expectedToBeEnabled\"],false,true],"
            + "[\"variation\",[\"expectedVariation\"],\"missing\",null],"
            + "[\"variable\",[\"expectedVariables\",\"text\"],\"wrong\",\"actual\"],"
            + "[\"evaluation\",[\"expectedEvaluations\",\"flag\",\"enabled\"],false,true],"
            + "[\"evaluation\",[\"expectedEvaluations\",\"variation\",\"featureKey\"],\"wrong\",\"flag\"],"
            + "[\"evaluation\",[\"expectedEvaluations\",\"variables\",\"text\",\"variableValue\"],\"wrong\",\"actual\"]],"
            + "[[\"variable\",[\"expectedValue\"],\"wrong\",\"actual\"],"
            + "[\"evaluation\",[\"expectedEvaluation\",\"variableValue\"],\"wrong\",\"actual\"]],"
            + "[[\"segment\",[\"expectedToMatch\"],false,true]]]");
        assertEquals(3, results.size());
        for (int spec = 0; spec < results.size(); spec++) {
            JsonNode errors = results.get(spec).at("/cases/0/errors");
            JsonNode rows = expected.get(spec);
            int instances = spec == 2 ? 1 : 2;
            assertFalse(results.get(spec).get("passed").asBoolean());
            assertEquals(rows.size() * instances, errors.size());
            for (int child = 0; child < instances; child++) {
                for (int index = 0; index < rows.size(); index++) {
                    JsonNode row = rows.get(index), error = errors.get(child * rows.size() + index);
                    var path = json.createArrayNode().add("assertions").add(7);
                    if (child == 1) path.add("children").add(0);
                    for (JsonNode part : row.get(1)) path.add(part);
                    assertEquals(row.get(0), error.get("type"));
                    assertEquals(path, error.get("path"));
                    assertEquals(row.get(2), error.get("expected"));
                    assertEquals(row.get(3), error.get("actual"));
                    if (child == 1) assertEquals(0, error.at("/details/childIndex").asInt(-1));
                }
            }
        }
    }

    @Test
    void cacheIsolatesFlatAndConfiguredSelections(@TempDir Path project) throws Exception {
        var targets = List.of("web", "web-target-mobile", "mobile", "Café 東京 🧪\"\\", "[null,\"web\"]");
        for (boolean flat : new boolean[] {false, true}) {
            List<String> environments = flat ? java.util.Collections.singletonList(null)
                : List.of("prod", "prod-target-web", "prod-target-web-target-mobile", "false",
                    "__no_environment__", "Café 東京 🧪\"\\", "[null,\"web\"]");
            var selectedTargets = new ArrayList<String>();
            selectedTargets.add(null);
            selectedTargets.addAll(targets);
            var assertions = json.createArrayNode();
            var builds = new java.util.LinkedHashMap<String, String>();
            for (String environment : environments) {
                for (String target : selectedTargets) {
                    String value = json.writeValueAsString(java.util.Arrays.asList(environment, target));
                    assertions.addObject().put("environment", environment).put("target", target)
                        .put("expectedValue", value);
                    String command = "npx featurevisor build --json"
                        + (environment == null ? "" : " --environment=" + environment)
                        + (target == null ? "" : " --target=" + target);
                    builds.put(command, "{\"schemaVersion\":\"2\",\"revision\":\"cache\",\"segments\":{},"
                        + "\"features\":{},\"variables\":{\"selection\":{\"type\":\"string\",\"defaultValue\":"
                        + json.writeValueAsString(value) + "}}}");
                }
            }
            var calls = new ArrayList<String>();
            CLI cli = new CLI((directory, command) -> {
                if (command.contains(" config ")) return json.writeValueAsString(
                    java.util.Map.of("sets", false, "environments", flat ? false : environments));
                if (command.contains("--segments")) return "[]";
                if (command.contains("--targets")) return json.writeValueAsString(
                    targets.stream().map(target -> java.util.Map.of("key", target)).collect(java.util.stream.Collectors.toList()));
                if (command.contains("--tests")) return executionProtocol(
                    "[{\"key\":\"selection\",\"variable\":\"selection\",\"assertions\":" + assertions + "}]");
                assertTrue(builds.containsKey(command), command);
                calls.add(command);
                return builds.get(command);
            });
            Execution execution = execute(cli, "test", "--json", "--projectDirectoryPath=" + project);
            assertEquals(0, execution.code, execution.stdout);
            assertEquals(builds.size(), calls.size());
            assertEquals(builds.keySet(), new java.util.HashSet<>(calls));
            JsonNode cases = json.readTree(execution.stdout).at("/results/0/cases");
            assertEquals(assertions.size(), cases.size());
            for (int i = 0; i < assertions.size(); i++) {
                assertEquals(assertions.get(i).get("environment"), cases.get(i).get("environment"));
                assertEquals(assertions.get(i).get("target"), cases.get(i).get("target"));
                assertTrue(cases.get(i).get("passed").asBoolean());
            }
        }
    }

    @Test
    void rejectsExplicitSetsForNonSetsProjectsBeforeBuilding(@TempDir Path project) throws Exception {
        for (String command : List.of("test", "benchmark", "assess-distribution")) {
            for (String selectedSet : List.of("", "web")) {
                var calls = new ArrayList<String>();
                CLI cli = new CLI((directory, delegated) -> {
                    calls.add(delegated);
                    if (delegated.contains(" config ")) return "{\"sets\":false,\"environments\":false}";
                    throw new AssertionError("Unexpected delegation: " + delegated);
                });
                Execution execution = execute(cli, command, "--json", "--feature=flag", "--n=1",
                    "--set=" + selectedSet, "--projectDirectoryPath=" + project);
                assertEquals(1, execution.code);
                assertTrue((execution.stdout + execution.stderr).contains(selectedSet.isEmpty() ? "must not be empty" : "Omit --set"));
                if (selectedSet.isEmpty()) assertTrue(calls.isEmpty());
                assertTrue(calls.stream().allMatch(call -> call.contains(" config ")));
            }
        }
    }

    @Test
    void jsonOutputSurvivesAnAsciiConsole(@TempDir Path project) throws Exception {
        String value = "Café 東京 🧪";
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return "{\"schemaVersion\":\"2\",\"revision\":\"unicode\","
                + "\"segments\":{},\"features\":{},\"variables\":{\"greeting\":{\"type\":\"string\","
                + "\"defaultValue\":" + json.writeValueAsString(value) + "}}}";
            return executionProtocol("[{\"variable\":\"greeting\",\"_test\":{\"source\":\"tests/greeting.spec.json\"},"
                + "\"assertions\":[{\"description\":\"Café 東京 🧪\",\"expectedValue\":\"other\","
                + "\"_case\":{\"id\":\"unicode-case\",\"assertionIndex\":0,\"matrixIndex\":null,"
                + "\"matrixValues\":{}}}]}]");
        });

        Execution machine = executeWithCharset(cli, StandardCharsets.US_ASCII, "test", "--json",
            "--projectDirectoryPath=" + project);
        assertEquals(1, machine.code);
        assertEquals(value, json.readTree(machine.stdout)
            .at("/results/0/cases/0/errors/0/actual").asText());
        assertEquals(value, json.readTree(machine.stdout)
            .at("/results/0/cases/0/description").asText());

        Execution datafile = executeWithCharset(cli, StandardCharsets.US_ASCII, "test", "--showDatafile",
            "--projectDirectoryPath=" + project);
        assertEquals(1, datafile.code);
        assertTrue(datafile.stdout.contains("\\u00E9"), datafile.stdout);
        assertTrue(datafile.stdout.contains("\\u6771"), datafile.stdout);
        assertTrue(datafile.stdout.contains("\\uD83E\\uDDEA"), datafile.stdout);
    }

    @Test
    void emitsStructuredScenarioReportWithAuthoredPathsAndSeparateCounts(@TempDir Path project)
            throws Exception {
        String id = "[null,\"scenario\",\"limits.spec\",0,0]";
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains("--without-tests") && command.contains("--features")) {
                return "[{\"key\":\"unusedFeature\"}]";
            }
            if (command.contains("--without-tests") && command.contains("--variables")) {
                return "[{\"key\":\"unusedVariable\"}]";
            }
            if (command.contains(" build ")) return datafileJson();
            return executionProtocol("[{\"scenario\":\"Connection limits\","
                + "\"_test\":{\"source\":\"tests/scenarios/limits.spec.json\",\"set\":null,\"entityType\":\"scenario\"},"
                + "\"assertions\":[{\"description\":\"Bounds remain valid\","
                + "\"_case\":{\"id\":" + json.writeValueAsString(id)
                + ",\"assertionIndex\":0,\"matrixIndex\":0,\"matrixValues\":{\"country\":\"nl\"}},"
                + "\"context\":{\"country\":\"nl\",\"checks\":\"context checks\","
                + "\"custom\":\"context custom\"},"
                + "\"checks\":[{\"key\":\"bounds\",\"description\":\"Minimum must not exceed maximum\","
                + "\"variable\":\"minimum\",\"operator\":\"lessThanOrEquals\","
                + "\"compareTo\":{\"variable\":\"maximum\"}}],"
                + "\"children\":[{\"checks\":[{\"key\":\"child-bounds\",\"variable\":\"minimum\","
                + "\"operator\":\"lessThanOrEquals\",\"compareTo\":{\"variable\":\"maximum\"}}]}]}]}]");
        });

        Execution execution = execute(cli, "test", "--json", "--pretty", "--verbose", "--showDatafile",
            "--coverage", "--uncovered", "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode report = json.readTree(execution.stdout);
        assertEquals(1, report.get("schemaVersion").asInt());
        assertEquals("java", report.get("runner").asText());
        assertFalse(report.get("passed").asBoolean());
        assertEquals(1, report.at("/summary/specs/failed").asInt());
        assertEquals(0, report.at("/summary/entityAssertions/failed").asInt());
        assertEquals(1, report.at("/summary/scenarioCases/failed").asInt());
        assertEquals("tests/scenarios/limits.spec.json", report.at("/results/0/source").asText());
        assertEquals(id, report.at("/results/0/cases/0/id").asText());
        assertEquals("nl", report.at("/results/0/cases/0/matrixValues/country").asText());
        assertEquals("context checks", report.at("/results/0/cases/0/context/checks").asText());
        assertEquals("context custom", report.at("/results/0/cases/0/context/custom").asText());
        assertEquals(json.readTree("[\"checks\",0]").toString(),
            report.at("/results/0/cases/0/errors/0/path").toString());
        assertEquals(true, report.at("/results/0/cases/0/errors/0/expected").asBoolean());
        assertEquals(false, report.at("/results/0/cases/0/errors/0/actual").asBoolean());
        assertEquals("bounds", report.at("/results/0/cases/0/errors/0/details/checkKey").asText());
        assertEquals(12, report.at("/results/0/cases/0/errors/0/details/evaluated/0/actual").asInt());
        assertEquals(10, report.at("/results/0/cases/0/errors/0/details/evaluated/0/expected").asInt());
        assertEquals(json.readTree("[\"children\",0,\"checks\",0]").toString(),
            report.at("/results/0/cases/0/errors/1/path").toString());
        assertEquals("--case=" + id, report.at("/results/0/cases/0/rerun/args/1").asText());
        assertEquals("--projectDirectoryPath=" + project,
            report.at("/results/0/cases/0/rerun/args/2").asText());
        assertEquals("maven-exec", report.at("/results/0/cases/0/rerun/argumentEncoding").asText());
        assertEquals("unusedFeature", report.at("/uncovered/0/features/0").asText());
        assertEquals("unusedVariable", report.at("/uncovered/0/variables/0").asText());
        assertEquals("observed-selections", report.at("/coverage/mode").asText());
        assertTrue(report.at("/coverage/selections").findValuesAsText("variableKey").contains("minimum"));
        JsonNode minimumSelection = null;
        for (JsonNode selection : report.at("/coverage/selections")) {
            if ("minimum".equals(selection.path("variableKey").asText())) minimumSelection = selection;
        }
        assertNotNull(minimumSelection);
        assertEquals(json.readTree("[\"netherlands\"]"), minimumSelection.get("overridePath"));
        assertTrue(calls.stream().anyMatch(command -> command.contains("--apply-matrix")
            && command.contains("--execution-metadata") && command.contains("--execution-protocol")));
    }

    @Test
    void findsAnExactCaseAcrossSetsBeforeBuildingAndForwardsItUnchanged(@TempDir Path project)
            throws Exception {
        Files.createDirectories(project.resolve("sets/production"));
        Files.createDirectories(project.resolve("sets/staging"));
        String stagingId = "[\"staging\",\"variable\",\"minimum.spec\",\"it's exact\",null]";
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false,\"sets\":true}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            if (command.contains("--set=staging")) return variableSpec(stagingId, "staging");
            return variableSpec("[\"production\",\"variable\",\"minimum.spec\",0,null]", "production");
        });

        Execution execution = execute(cli, "test", "--json", "--case=" + stagingId,
            "--projectDirectoryPath=" + project);
        assertEquals(0, execution.code, execution.stdout);
        JsonNode report = json.readTree(execution.stdout);
        assertEquals(1, report.at("/summary/assertions/passed").asInt());
        assertEquals("staging", report.at("/results/0/set").asText());
        assertEquals(stagingId, report.at("/results/0/cases/0/id").asText());
        assertEquals("--set=staging", report.at("/results/0/cases/0/rerun/args/3").asText());
        assertTrue(calls.stream().anyMatch(command -> command.contains("--set=production")
            && command.contains("--execution-metadata") && !command.contains("--case=")));
        assertTrue(calls.stream().anyMatch(command -> command.contains("--set=staging")
            && command.contains("--case=" + stagingId)));
        assertTrue(calls.stream().filter(command -> command.contains(" build "))
            .allMatch(command -> command.contains("--set=staging")));
    }

    @Test
    void runsAnExactCaseInANonSetProjectWithoutCrossSetDiscovery(@TempDir Path project)
            throws Exception {
        String id = "[null,\"variable\",\"minimum.spec\",\"it's exact\",null]";
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            return variableSpec(id, null);
        });

        Execution execution = execute(cli, "test", "--json", "--case=" + id,
            "--projectDirectoryPath=" + project);
        assertEquals(0, execution.code, execution.stdout);
        JsonNode report = json.readTree(execution.stdout);
        assertEquals(1, report.at("/summary/assertions/passed").asInt());
        assertEquals(id, report.at("/results/0/cases/0/id").asText());
        assertTrue(calls.stream().anyMatch(command -> command.contains("--case=" + id)));
        assertEquals(1, calls.stream().filter(command -> command.contains("--tests")).count());
    }

    @Test
    void unknownExactCaseFailsBeforeAnyDatafileBuild(@TempDir Path project) throws Exception {
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--tests") && command.contains("--case=")) return executionProtocol("[]");
            if (command.contains("--tests")) return variableSpec(
                "[null,\"variable\",\"minimum.spec\",0,null]", null);
            throw new AssertionError("Unexpected command after failed case selection: " + command);
        });

        Execution execution = execute(cli, "test", "--json", "--case=not-found",
            "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode report = json.readTree(execution.stdout);
        assertFalse(report.get("passed").asBoolean());
        assertEquals(1, report.get("errors").size());
        assertEquals(0, report.at("/summary/assertions/passed").asInt());
        assertTrue(calls.stream().noneMatch(command -> command.contains(" build ")));
    }

    @Test
    void humanOutputUsesCheckNamesAuthoredSourceAndQuotedRerun(@TempDir Path project)
            throws Exception {
        Path projectWithSpaces = Files.createDirectories(project.resolve("project with spaces"));
        String id = "[null,\"scenario\",\"limits.spec\",\"it's a \\\"quoted\\\" case\",null]";
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            return executionProtocol("[{\"scenario\":\"Connection limits\","
                + "\"_test\":{\"source\":\"tests/scenarios/limits.spec.json\",\"set\":null},"
                + "\"assertions\":[{\"description\":\"Named failure\","
                + "\"_case\":{\"id\":" + json.writeValueAsString(id)
                + ",\"assertionIndex\":0,\"matrixIndex\":null,\"matrixValues\":{}},"
                + "\"checks\":[{\"key\":\"bounds\",\"description\":\"Minimum must not exceed maximum\","
                + "\"variable\":\"minimum\",\"operator\":\"lessThanOrEquals\","
                + "\"compareTo\":{\"variable\":\"maximum\"}}]}]}]");
        });

        Execution execution = execute(cli, "test", "--projectDirectoryPath=" + projectWithSpaces);
        assertEquals(1, execution.code);
        assertTrue(execution.stdout.contains("Testing: tests/scenarios/limits.spec.json"));
        assertTrue(execution.stdout.contains("check \"bounds\" (Minimum must not exceed maximum)"));
        assertFalse(execution.stdout.contains("checks[0]"), execution.stdout);
        assertTrue(execution.stdout.contains("Scenario cases: 0 passed, 1 failed"));
        assertTrue(execution.stdout.contains("Total cases: 0 passed, 1 failed"));
        assertTrue(execution.stdout.contains("-Dexec.args="));

        String rerunCommand = execution.stdout.lines()
            .filter(line -> line.startsWith("    Rerun (POSIX shell): "))
            .map(line -> line.substring("    Rerun (POSIX shell): ".length()))
            .findFirst().orElseThrow();
        String fixtureCommand = rerunCommand.replace(
            "-Dexec.mainClass=com.featurevisor.cli.CLI",
            "'-Dexec.mainClass=com.featurevisor.cli.CLIReportingTest$MavenArgumentsFixture' "
                + "-Dexec.classpathScope=test"
        );
        Path repository = Path.of(System.getProperty("user.dir"));
        if (repository.getFileName().toString().equals("featurevisor-sdk")) repository = repository.getParent();
        Process process = new ProcessBuilder("bash", "-lc", fixtureCommand)
            .directory(repository.toFile())
            .start();
        int exitCode = process.waitFor();
        String fixtureStdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String fixtureStderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, exitCode, fixtureStdout + fixtureStderr);
        int jsonStart = fixtureStdout.indexOf("[\"test\"");
        assertTrue(jsonStart >= 0, fixtureStdout);
        assertEquals(json.valueToTree(List.of(
            "test", "--case=" + id, "--projectDirectoryPath=" + projectWithSpaces
        )), json.readTree(fixtureStdout.substring(jsonStart)));
    }

    @Test
    void humanChildExpectationsUseOneBasedLabelsWithoutChangingJSONPaths(@TempDir Path project) throws Exception {
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            return executionProtocol("[{\"variable\":\"minimum\",\"key\":\"minimum.spec\","
                + "\"_test\":{\"source\":\"tests/minimum.spec.json\"},"
                + "\"assertions\":[{\"description\":\"child failure\","
                + "\"children\":[{\"expectedValue\":99,\"expectedEvaluation\":{\"variableValue\":99}}]}]}]");
        });

        Execution human = execute(cli, "test", "--projectDirectoryPath=" + project);
        assertEquals(1, human.code);
        assertTrue(human.stdout.contains("Child 1, expectedValue: expected 99"), human.stdout);
        assertTrue(human.stdout.contains("Child 1, expectedEvaluation.variableValue: expected 99"), human.stdout);
        assertFalse(human.stdout.contains("children[0]"), human.stdout);

        Execution machine = execute(cli, "test", "--json", "--projectDirectoryPath=" + project);
        JsonNode errors = json.readTree(machine.stdout).at("/results/0/cases/0/errors");
        assertEquals(json.readTree("[\"assertions\",0,\"children\",0,\"expectedValue\"]"), errors.get(0).get("path"));
        assertEquals(json.readTree("[\"assertions\",0,\"children\",0,\"expectedEvaluation\",\"variableValue\"]"), errors.get(1).get("path"));
    }

    @Test
    void entityErrorsUseTheAuthoredAssertionIndexAndExplicitMissingEvidence(@TempDir Path project)
            throws Exception {
        String id = "[null,\"variable\",\"settings.spec\",\"missing-path\",null]";
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets") || command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            return executionProtocol("[{\"variable\":\"settings\",\"_test\":{\"source\":\"tests/settings.spec.json\","
                + "\"set\":null},\"assertions\":[{\"description\":\"Missing is not null\","
                + "\"_case\":{\"id\":" + json.writeValueAsString(id)
                + ",\"assertionIndex\":4,\"matrixIndex\":null,\"matrixValues\":{}},"
                + "\"checks\":[{\"key\":\"missing-value\",\"variable\":\"settings\","
                + "\"path\":\"absent\",\"operator\":\"isNull\"}]}]}]");
        });

        Execution execution = execute(cli, "test", "--json", "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode error = json.readTree(execution.stdout).at("/results/0/cases/0/errors/0");
        assertEquals(json.readTree("[\"assertions\",4,\"checks\",0]"), error.get("path"));
        assertEquals(json.readTree("[\"assertions\",4,\"checks\",0]"),
            error.at("/details/evaluated/0/path"));
        assertTrue(error.at("/details/evaluated/0/actualMissing").asBoolean());
        assertEquals("undefined", error.at("/details/evaluated/0/actual/$type").asText());
    }

    @Test
    void targetFiltersSelectTargetedAndUntargetedCasesAndRemainInReruns(@TempDir Path project)
            throws Exception {
        String mobileId = "[null,\"variable\",\"minimum.spec\",\"mobile\",null]";
        List<String> calls = new ArrayList<>();
        CLI cli = new CLI((directory, command) -> {
            calls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--targets")) return "[{\"key\":\"web\"},{\"key\":\"mobile\"}]";
            if (command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafileJson();
            return targetedVariableSpec();
        });

        Execution execution = execute(cli, "test", "--json", "--target=web",
            "--projectDirectoryPath=" + project);
        assertEquals(0, execution.code);
        JsonNode report = json.readTree(execution.stdout);
        assertEquals(2, report.at("/summary/assertions/passed").asInt());
        assertEquals("web", report.at("/results/0/cases/0/target").asText());
        assertEquals("--target=web", report.at("/results/0/cases/0/rerun/args/3").asText());
        assertTrue(calls.stream().anyMatch(command -> command.contains(" build ") && command.contains("--target=web")));
        assertTrue(calls.stream().noneMatch(command -> command.contains(" build ") && command.contains("--target=mobile")));

        List<String> rejectedCalls = new ArrayList<>();
        CLI rejected = new CLI((directory, command) -> {
            rejectedCalls.add(command);
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--tests") && command.contains("--case=")) {
                return executionProtocol("[{\"variable\":\"minimum\",\"_test\":{\"source\":\"tests/minimum.spec.json\","
                    + "\"set\":null},\"assertions\":[" + targetedAssertion("mobile", "mobile", 1) + "]}]");
            }
            if (command.contains("--tests")) return targetedVariableSpec();
            throw new AssertionError("Excluded exact case must fail before builds: " + command);
        });
        Execution rejectedExecution = execute(rejected, "test", "--json", "--target=web",
            "--case=" + mobileId, "--projectDirectoryPath=" + project);
        assertEquals(1, rejectedExecution.code);
        assertEquals(0, json.readTree(rejectedExecution.stdout).at("/summary/assertions/passed").asInt());
        assertTrue(rejectedCalls.stream().noneMatch(command -> command.contains(" build ")));
    }

    @Test
    void rejectsInvalidExecutionProtocolBeforeBuilds(@TempDir Path project) throws Exception {
        List<String> invalid = List.of(
            "[]",
            "{}",
            "{\"kind\":null,\"schemaVersion\":1,\"tests\":[]}",
            "{\"kind\":\"wrong\",\"schemaVersion\":1,\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":null,\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":true,\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":\"1\",\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":2,\"tests\":[]}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":null}",
            "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":[1]}"
        );
        for (String document : invalid) {
            List<String> calls = new ArrayList<>();
            CLI cli = new CLI((directory, command) -> {
                calls.add(command);
                if (command.contains(" config ")) return "{\"environments\":false}";
                if (command.contains("--tests")) return document;
                throw new AssertionError("Protocol rejection must stop before " + command);
            });
            Execution execution = execute(cli, "test", "--json",
                "--projectDirectoryPath=" + project);
            assertEquals(1, execution.code, document);
            assertTrue(calls.stream().noneMatch(command -> command.contains(" build ")), document);
        }
    }

    @Test
    void preflightChecksChildrenAndSelectedTargetsBeforeEvaluation(@TempDir Path project)
            throws Exception {
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return "{\"environments\":false}";
            if (command.contains("--segments")) return "[]";
            if (command.contains("--targets")) return "[{\"key\":\"mobile\"}]";
            if (command.contains(" build ")) {
                boolean target = command.contains("--target=mobile");
                return "{\"schemaVersion\":\"2\",\"revision\":\""
                    + (target ? "target" : "base")
                    + "\",\"segments\":{},\"features\":"
                    + (target ? "{}" : "{\"baseOnly\":{\"bucketBy\":[\"userId\"],\"traffic\":[]}}")
                    + ",\"variables\":{\"minimum\":{\"type\":\"integer\",\"defaultValue\":5}}}";
            }
            return executionProtocol("["
                + "{\"variable\":\"minimum\",\"assertions\":[{\"expectedValue\":5,"
                + "\"checks\":[{\"operator\":\"equals\",\"value\":5}],"
                + "\"children\":[{\"checks\":[{\"or\":["
                + "{\"operator\":\"equals\",\"value\":5},"
                + "{\"variable\":\"missing\",\"operator\":\"equals\",\"value\":0}]}]}]}]},"
                + "{\"scenario\":\"selected target\",\"assertions\":[{\"target\":\"mobile\","
                + "\"checks\":[{\"feature\":\"baseOnly\",\"enabled\":true}]}]}]");
        });

        Execution execution = execute(cli, "test", "--json", "--coverage",
            "--projectDirectoryPath=" + project);
        assertEquals(1, execution.code);
        JsonNode report = json.readTree(execution.stdout);
        assertEquals(2, report.at("/summary/assertions/failed").asInt());
        assertEquals(0, report.at("/coverage/selections").size());
    }

    private String variableSpec(String id, String set) throws IOException {
        return executionProtocol("[{\"variable\":\"minimum\",\"_test\":{\"source\":\""
            + (set == null ? "tests/minimum.spec.json" : "sets/" + set + "/tests/minimum.spec.json")
            + "\",\"set\":" + json.writeValueAsString(set) + ",\"entityType\":\"variable\"},"
            + "\"assertions\":[{\"description\":\"Minimum\",\"expectedValue\":12,"
            + "\"_case\":{\"id\":" + json.writeValueAsString(id)
            + ",\"assertionIndex\":0,\"matrixIndex\":null,\"matrixValues\":{}}}]}]");
    }

    private String targetedVariableSpec() throws IOException {
        return executionProtocol("[{\"variable\":\"minimum\",\"_test\":{\"source\":\"tests/minimum.spec.json\","
            + "\"set\":null},\"assertions\":["
            + targetedAssertion("web", "web", 0) + ","
            + targetedAssertion("mobile", "mobile", 1) + ","
            + targetedAssertion("base", null, 2) + "]}]");
    }

    private String targetedAssertion(String key, String target, int index) throws IOException {
        String id = "[null,\"variable\",\"minimum.spec\",\"" + key + "\",null]";
        return "{\"description\":\"" + key + "\",\"expectedValue\":12"
            + (target == null ? "" : ",\"target\":\"" + target + "\"")
            + ",\"_case\":{\"id\":" + json.writeValueAsString(id) + ",\"assertionIndex\":" + index
            + ",\"matrixIndex\":null,\"matrixValues\":{}}}";
    }

    private String datafileJson() {
        return "{\"schemaVersion\":\"2\",\"revision\":\"report\",\"segments\":{},\"features\":{},"
            + "\"variables\":{\"minimum\":{\"type\":\"integer\",\"defaultValue\":5,"
            + "\"overrides\":[{\"key\":\"nl\",\"keyPath\":[\"netherlands\"],\"segments\":\"*\",\"value\":12}]},"
            + "\"maximum\":{\"type\":\"integer\",\"defaultValue\":10},"
            + "\"settings\":{\"type\":\"object\",\"defaultValue\":{\"nullable\":null}}}}";
    }

    private String executionProtocol(String tests) {
        return "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":"
            + tests + "}";
    }

    private Execution execute(CLI cli, String... args) throws Exception {
        return executeWithCharset(cli, StandardCharsets.UTF_8, args);
    }

    private Execution executeWithCharset(CLI cli, Charset charset, String... args) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(stdout, true, charset.name()));
            System.setErr(new PrintStream(stderr, true, charset.name()));
            int code = new CommandLine(cli).execute(args);
            return new Execution(code, stdout.toString(StandardCharsets.UTF_8.name()),
                stderr.toString(StandardCharsets.UTF_8.name()));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static final class Execution {
        final int code;
        final String stdout;
        final String stderr;

        Execution(int code, String stdout, String stderr) {
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public static final class MavenArgumentsFixture {
        private MavenArgumentsFixture() {}

        public static void main(String[] args) throws Exception {
            System.out.print(new ObjectMapper().writeValueAsString(args));
        }
    }
}
