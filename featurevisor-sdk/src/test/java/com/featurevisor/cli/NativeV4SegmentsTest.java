package com.featurevisor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import static org.junit.jupiter.api.Assertions.*;

final class NativeV4SegmentsTest {
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode run(Path project, String config, String datafile, String tests, int expectedCode, String... options) throws Exception {
        CLI cli = new CLI((directory, command) -> {
            if (command.contains(" config ")) return config;
            if (command.contains("--targets")) return "[{\"key\":\"thin\"}]";
            if (command.contains("--segments")) return "[]";
            if (command.contains(" build ")) return datafile;
            if (!command.contains("--execution-protocol")) throw new AssertionError(command);
            return "{\"kind\":\"featurevisor.test-execution\",\"schemaVersion\":1,\"tests\":" + tests + "}";
        });
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream oldOut = System.out, oldErr = System.err;
        try (PrintStream stdout = new PrintStream(output, true, StandardCharsets.UTF_8);
                PrintStream stderr = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setOut(stdout); System.setErr(stderr);
            List<String> args = new ArrayList<>(List.of("test", "--json", "--coverage", "--project-directory-path=" + project));
            args.addAll(List.of(options));
            int code = new CommandLine(cli).execute(args.toArray(new String[0]));
            assertEquals(expectedCode, code, output + "\n" + errors);
        } finally { System.setOut(oldOut); System.setErr(oldErr); }
        return json.readTree(output.toString(StandardCharsets.UTF_8));
    }
    private static final String DATAFILE = """
        {"schemaVersion":"4","revision":"segments","segments":{},"features":{
          "gate":{"bucketBy":"userId","traffic":[{"key":"all","conditions":"*","percentage":50000}]}
        },"variables":{"source":{"type":"object","defaultValue":{"items":[false]}}}}
        """;

    @Test void ownerAndChecksUseSdkGettersCompiledPathsAtAndChildStickyState(@TempDir Path project) throws Exception {
        JsonNode report = run(project, "{\"environments\":false}", DATAFILE, """
            [{"segment":"dependent","_segments":{
              "dependent":{"conditions":["inner",{"feature":"gate"}]},
              "inner":{"conditions":{"variable":"source","path":["items",0],"operator":"equals","value":true}}
            },"assertions":[
              {"at":10,"stickyVariables":{"source":{"items":[true]}},"expectedToMatch":true},
              {"at":90,"stickyVariables":{"source":{"items":[true]}},"expectedToMatch":false},
              {"at":10,"expectedToMatch":false}
            ]},{"scenario":"checks","_segments":{
              "dependent":{"conditions":["inner",{"feature":"gate"}]},
              "inner":{"conditions":{"variable":"source","path":["items",0],"operator":"equals","value":true}}
            },"assertions":[{"at":10,"stickyVariables":{"source":{"items":[true]}},
              "checks":[{"segment":"dependent","matches":true}],"children":[
                {"stickyVariables":{"source":{"items":[true]}},"checks":[{"segment":"dependent","matches":true}]},
                {"checks":[{"segment":"dependent","matches":false}]}]}]}]
            """, 0);
        assertTrue(report.path("passed").asBoolean());
        for (JsonNode selection : report.path("coverage").path("selections")) {
            assertFalse(selection.path("featureKey").asText().startsWith("__featurevisor_segment_test__"));
        }
    }

    @Test void selectedTargetSpecializationRemainsAuthoritativeForOwnersAndChecks(@TempDir Path project) throws Exception {
        String selected = DATAFILE.replace("\"segments\":{}", "\"segments\":{\"dutch\":{\"conditions\":\"*\"}}");
        JsonNode report = run(project, "{\"environments\":false}", selected, """
            [{"segment":"dutch","_segments":{"dutch":{"conditions":{"attribute":"country","operator":"equals","value":"nl"}}},
              "assertions":[{"target":"thin","context":{"country":"us"},"expectedToMatch":true}]},
             {"scenario":"specialized","_segments":{"dutch":{"conditions":{"attribute":"country","operator":"equals","value":"nl"}}},
              "assertions":[{"target":"thin","context":{"country":"us"},"checks":[{"segment":"dutch","matches":true}]}]}]
            """, 0, "--target=thin");
        assertTrue(report.path("passed").asBoolean());
    }

    @Test void attributeChainsNeedNoEnvironmentButDependentOwnersDo(@TempDir Path project) throws Exception {
        String config = "{\"environments\":[\"staging\"]}";
        JsonNode report = run(project, config, DATAFILE, """
            [{"segment":"outer","_segments":{"outer":{"conditions":"inner"},"inner":{"conditions":"*"}},
               "assertions":[{"expectedToMatch":true}]}]
            """, 0);
        assertTrue(report.path("passed").asBoolean());
        report = run(project, config, DATAFILE, """
            [{"segment":"dependent","_segments":{"dependent":{"conditions":{"feature":"gate"}}},
               "assertions":[{"expectedToMatch":false}]}]
            """, 1);
        assertFalse(report.path("passed").asBoolean());
        assertTrue(report.toString().contains("selected datafile"));
    }

    @Test void preflightVisitsSkippedTransitiveBranchesBeforeAnyOwnerGetter(@TempDir Path project) throws Exception {
        JsonNode report = run(project, "{\"environments\":false}", DATAFILE, """
            [{"feature":"gate","_segments":{"outer":{"conditions":{"or":["*","inner"]}},
               "inner":{"conditions":{"variable":"missing","operator":"notNull"}}},
              "assertions":[{"at":10,"expectedToBeEnabled":true,"checks":[
                {"or":[{"feature":"gate","enabled":true},{"segment":"outer","matches":true}]}]}]}]
            """, 1);
        assertFalse(report.path("passed").asBoolean());
        assertTrue(report.toString().contains("missing"));
        assertEquals(0, report.path("coverage").path("selections").size());
    }
}
