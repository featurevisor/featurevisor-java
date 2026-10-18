package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EvaluationBoundaryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT_DATA = """
        {"schemaVersion":"4","revision":"candidate","segments":{},"features":{},
         "variables":{"value":{"type":"string","defaultValue":"candidate"}}}
        """;

    @Test void rootParserRejectsTrailingTokensButAcceptsTrailingWhitespace() throws Exception {
        assertEquals("candidate", DatafileContent.fromJson(ROOT_DATA + " \n\t ").getRevision());
        for (String tail : List.of(" {}", " true", " null", " 1", " garbage"))
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> DatafileContent.fromJson(ROOT_DATA + tail), tail);
    }

    @Test void constructorRejectsTrailingRootTokensWithoutThrowingOrAcceptingThePrefix() {
        for (String tail : List.of(" {}", " true", " garbage")) {
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            Featurevisor f = assertDoesNotThrow(() -> Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafileString(ROOT_DATA + tail).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add)));
            try {
                assertEquals("unknown", f.getRevision());
                assertTrue(f.getVariableKeys().isEmpty());
                assertEquals(1, diagnostics.size());
                FeaturevisorDiagnostic diagnostic = diagnostics.get(0);
                assertEquals("invalid_datafile", diagnostic.getCode());
                assertEquals("Could not parse datafile", diagnostic.getMessage());
                assertEquals(FeaturevisorLogLevel.ERROR, diagnostic.getLevel());
                assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class, diagnostic.getOriginalError());
                assertTrue(f.setDatafile(ROOT_DATA));
                assertEquals("candidate", f.getRevision());
                assertEquals("candidate", f.getVariable("value"));
            } finally { f.close(); }
        }
    }

    @Test void stringSetterRejectsTrailingRootTokensWithoutChangingTheAcceptedSnapshot() {
        for (boolean replace : List.of(false, true)) {
            Featurevisor[] holder = {null};
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            holder[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafileString(ROOT_DATA.replace("candidate", "accepted"))
                .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostic -> {
                    diagnostics.add(diagnostic);
                    assertEquals("accepted", holder[0].getRevision());
                    assertEquals("accepted", holder[0].getVariable("value"));
                }));
            AtomicInteger events = new AtomicInteger();
            holder[0].on(FeaturevisorEventName.DATAFILE_SET, details -> events.incrementAndGet());
            try {
                int rejected = 0;
                for (String tail : List.of(" {}", " true", " null", " 1", " garbage")) {
                    assertFalse(holder[0].setDatafile(ROOT_DATA + tail, replace), tail);
                    assertEquals("accepted", holder[0].getRevision());
                    assertEquals("accepted", holder[0].getVariable("value"));
                    assertEquals(0, events.get());
                    assertEquals(++rejected, diagnostics.size());
                    FeaturevisorDiagnostic diagnostic = diagnostics.get(rejected - 1);
                    assertEquals("invalid_datafile", diagnostic.getCode());
                    assertEquals("Could not parse datafile", diagnostic.getMessage());
                    assertEquals(FeaturevisorLogLevel.ERROR, diagnostic.getLevel());
                    assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class, diagnostic.getOriginalError());
                }
                assertTrue(holder[0].setDatafile(ROOT_DATA + " \n\t ", replace));
                assertEquals("candidate", holder[0].getRevision());
                assertEquals("candidate", holder[0].getVariable("value"));
                assertEquals(1, events.get());
                assertEquals(rejected, diagnostics.size());
            } finally { holder[0].close(); }
        }
    }

    private static DatafileContent data(String version, Map<String, Object> segments,
            Map<String, Object> features, Map<String, Object> variables) throws Exception {
        return DatafileContent.fromJson(JSON.writeValueAsString(Map.of(
            "schemaVersion", version, "revision", "evaluation-boundaries", "segments", segments,
            "features", features, "variables", variables)));
    }

    @Test void trailingSelectorContentFailsThroughNotAndReportsOncePerEntityLoad() throws Exception {
        for (String version : List.of("2", "4")) {
            for (String raw : List.of("{\"not\":[]} trailing", "{\"not\":[]} {}", "\"*\" false")) {
                List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
                DatafileContent incoming = data(version, Map.of("bad", Map.of("conditions", raw)),
                    Map.of(), Map.of("subject", Map.of("type", "string", "defaultValue", "wrong",
                        "disabledValue", "blocked", "conditions", Map.of("not", List.of("bad")))));
                Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(incoming).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add));
                try {
                    assertEquals(version.equals("2") ? 1 : 0, diagnostics.size());
                    for (int run = 0; run < 2; run++) {
                        assertEquals("conditions_unmet", f.evaluateVariable("subject").getReason(), raw);
                        assertEquals("blocked", f.getVariable("subject"), raw);
                    }
                    assertEquals(1, diagnostics.size());
                    assertEquals("conditions_parse_error", diagnostics.get(0).getCode());
                    assertEquals(Map.of("conditions", raw, "entityType", "segment", "entityKey", "bad"),
                        diagnostics.get(0).getDetails());
                    assertEquals(raw, incoming.getSegments().get("bad").getConditions());
                } finally { f.close(); }
            }
        }
    }

    @Test void laterSourcesObserveCallbackDatafileChangesInParentAndChildCalls() throws Exception {
        for (boolean condition : List.of(false, true)) {
            for (boolean childMode : List.of(false, true)) {
                Featurevisor[] holder = {null};
                AtomicInteger reads = new AtomicInteger();
                DatafileContent update = data("4", Map.of(), Map.of(),
                    Map.of("source", Map.of("type", "integer", "defaultValue", 2)));
                FeaturevisorModule module = new FeaturevisorModule("update").afterEvaluation((result, options) -> {
                    if ("source".equals(options.getVariableKey())) reads.incrementAndGet();
                    if ("mutator".equals(options.getVariableKey())) assertTrue(holder[0].setDatafile(update));
                    return result;
                });
                holder[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(data("4", Map.of(), Map.of(), Map.of(
                        "source", Map.of("type", "integer", "defaultValue", 1),
                        "mutator", Map.of("type", "boolean", "defaultValue", true),
                        "subject", condition ? Map.of("compose", "source", "conditions", List.of(
                            Map.of("variable", "source", "operator", "equals", "value", 1),
                            Map.of("variable", "mutator", "operator", "equals", "value", true),
                            Map.of("variable", "source", "operator", "equals", "value", 2))) : Map.of("compose", List.of(
                            Map.of("key", "before", "variable", "source"),
                            Map.of("key", "mutation", "variable", "mutator"),
                            Map.of("key", "after", "variable", "source"))))))
                    .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
                ChildInstance child = holder[0].spawn();
                try {
                    assertEquals(condition ? 2 : Map.of("before", 1, "mutation", true, "after", 2),
                        childMode ? child.getVariable("subject") : holder[0].getVariable("subject"));
                    assertEquals(condition ? 3 : 2, reads.get());
                    assertEquals(2, holder[0].getVariable("source"));
                } finally { child.close(); holder[0].close(); }
            }
        }
    }

    @Test void laterSourcesObserveEffectiveContextMutationsWithoutRepeatingMemoizedReads() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("context").afterEvaluation((result, options) -> {
            if ("source".equals(options.getVariableKey())) {
                reads.incrementAndGet(); result.variableValue(options.getContext().get("value"));
            }
            if ("mutator".equals(options.getVariableKey())) options.getContext().put("value", 2);
            return result;
        });
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(data("4", Map.of(), Map.of(), Map.of(
                "source", Map.of("type", "integer", "defaultValue", 0),
                "mutator", Map.of("type", "boolean", "defaultValue", true),
                "subject", Map.of("compose", List.of(
                    Map.of("key", "before", "variable", "source"),
                    Map.of("key", "duplicate", "variable", "source"),
                    Map.of("key", "mutation", "variable", "mutator"),
                    Map.of("key", "after", "variable", "source"))))))
            .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
        try {
            Map<String, Object> context = new java.util.HashMap<>(Map.of("value", 1));
            for (int run = 1; run <= 2; run++) {
                assertEquals(Map.of("before", 1, "duplicate", 1, "mutation", true, "after", 2),
                    f.getVariable("subject", context));
                assertEquals(run * 2, reads.get());
                assertEquals(Map.of("value", 1), context);
            }
        } finally { f.close(); }
    }

    @Test void sourceGetterFallbacksStayOrdinaryAndNeverInheritTheOuterDefault() throws Exception {
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        List<Map<String, Object>> members = new ArrayList<>();
        for (String key : List.of("broken", "missing", "gated", "zero", "empty", "nil", "alias"))
            members.add(Map.of("key", key, "variable", key));
        members.add(Map.of("key", "duplicate", "variable", "broken"));
        members.add(Map.of("key", "flag", "feature", "absent"));
        members.add(Map.of("key", "variation", "feature", "absent", "variation", true));
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(data("4", Map.of(), Map.of(), Map.of(
                "broken", Map.of("type", "json", "defaultValue", "false true"),
                "gated", Map.of("type", "boolean", "defaultValue", true, "disabledValue", false, "conditions", List.of()),
                "zero", Map.of("type", "integer", "defaultValue", 0),
                "empty", Map.of("type", "string", "defaultValue", ""),
                "nil", JSON.readValue("{\"type\":\"object\",\"defaultValue\":null}", Map.class),
                "encoded", Map.of("type", "json", "defaultValue", "\"{oops\""),
                "alias", Map.of("type", "json", "compose", "encoded"),
                "subject", Map.of("compose", members))))
            .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add));
        Map<String, Object> expected = new java.util.HashMap<>();
        expected.put("broken", null); expected.put("duplicate", null); expected.put("missing", null);
        expected.put("gated", false); expected.put("zero", 0); expected.put("empty", "");
        expected.put("nil", null); expected.put("alias", "{oops"); expected.put("flag", false);
        expected.put("variation", null);
        try {
            for (int run = 1; run <= 2; run++) {
                Evaluation result = f.evaluateVariable("subject", Map.of(),
                    new Featurevisor.OverrideOptions().defaultVariableValue("outer"));
                assertNull(result.getError());
                assertEquals("variable_composed", result.getReason());
                assertEquals(expected, result.getVariableValue());
                assertEquals(run, diagnostics.stream().filter(d -> d.getMessage().equals("getVariable failed")).count());
            }
        } finally { f.close(); }
    }

    @Test void explicitNullRootsDifferFromAbsentRootsInBothSchemas() throws Exception {
        for (String version : List.of("2", "4")) {
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(DatafileContent.fromJson("{\"schemaVersion\":\"" + version + "\",\"revision\":\"null\",\"segments\":{},"
                    + "\"features\":{\"present\":{\"conditions\":null,\"bucketBy\":\"userId\",\"traffic\":[{\"key\":\"all\",\"conditions\":\"*\",\"percentage\":100000}]},"
                    + "\"absent\":{\"bucketBy\":\"userId\",\"traffic\":[{\"key\":\"all\",\"conditions\":\"*\",\"percentage\":100000}]}},"
                    + "\"variables\":{\"present\":{\"conditions\":null,\"type\":\"string\",\"defaultValue\":\"wrong\",\"disabledValue\":\"blocked\"},"
                    + "\"mixed\":{\"conditions\":null,\"requiredFeatures\":[\"absent\"],\"type\":\"string\",\"defaultValue\":\"wrong\",\"disabledValue\":\"blocked\"},"
                    + "\"absent\":{\"type\":\"string\",\"defaultValue\":\"ok\"}}}"))
                .logLevel(FeaturevisorLogLevel.FATAL));
            try {
                for (int run = 0; run < 2; run++) {
                    assertEquals("conditions_unmet", f.evaluateFlag("present").getReason());
                    assertTrue(f.isEnabled("absent"));
                    assertEquals("blocked", f.getVariable("present"));
                    assertEquals("blocked", f.getVariable("mixed"));
                    assertEquals("ok", f.getVariable("absent"));
                }
                assertTrue(JSON.valueToTree(f.getFeature("present")).get("conditions").isNull());
                assertFalse(JSON.valueToTree(f.getFeature("absent")).has("conditions"));
                assertTrue(JSON.readValue(JSON.writeValueAsString(f.getFeature("present")), Feature.class).hasConditions());
                assertFalse(JSON.readValue(JSON.writeValueAsString(f.getFeature("absent")), Feature.class).hasConditions());
                GlobalVariable present = f.evaluateVariable("present").getVariable();
                GlobalVariable absent = f.evaluateVariable("absent").getVariable();
                assertTrue(JSON.valueToTree(present).get("conditions").isNull());
                assertFalse(JSON.valueToTree(absent).has("conditions"));
                assertTrue(JSON.readValue(JSON.writeValueAsString(present), GlobalVariable.class).hasConditions());
                assertFalse(JSON.readValue(JSON.writeValueAsString(absent), GlobalVariable.class).hasConditions());
            } finally { f.close(); }
        }
    }

    @Test void notArraysAndGatedCompositionDefaultsMatchTheJsContract() throws Exception {
        for (String operand : List.of("[]", "[null]")) {
            for (String compose : List.of("\"source\"", "[{\"key\":\"value\",\"variable\":\"source\"}]")) {
                for (boolean disabled : List.of(false, true)) {
                    AtomicInteger reads = new AtomicInteger();
                    FeaturevisorModule module = new FeaturevisorModule("defaults").beforeEvaluation(options -> {
                        if (options.getDependencyOf() != null) reads.incrementAndGet();
                        return options.defaultVariableValue("module fallback");
                    });
                    Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                        .datafile(DatafileContent.fromJson("{\"schemaVersion\":\"4\",\"revision\":\"not\",\"segments\":{\"segment\":{\"conditions\":\"*\"}},\"features\":{},\"variables\":{"
                            + "\"source\":{\"type\":\"string\",\"defaultValue\":\"source\"},\"subject\":{\"compose\":" + compose
                            + ",\"conditions\":{\"not\":" + operand + "}" + (disabled ? ",\"disabledValue\":false" : "") + "}}}"))
                        .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
                    try {
                        Evaluation result = f.evaluateVariable("subject", Map.of(), new Featurevisor.OverrideOptions().defaultVariableValue("caller fallback"));
                        boolean matched = operand.equals("[null]");
                        assertNull(result.getError());
                        assertEquals(matched ? "variable_composed" : "conditions_unmet", result.getReason(), operand);
                        assertEquals(matched ? (compose.startsWith("[") ? Map.of("value", "source") : "source") : disabled ? false : null,
                            result.getVariableValue(), operand);
                        assertEquals(matched ? 1 : 0, reads.get());
                    } finally { f.close(); }
                }
            }
        }
    }

    @Test void malformedNonArrayNotOperandsNeverEnableFlags() throws Exception {
        for (String operand : List.of("null", "\"segment\"", "{}", "true", "1")) {
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(DatafileContent.fromJson("{\"schemaVersion\":\"4\",\"revision\":\"not\",\"segments\":{\"bad\":{\"conditions\":{\"not\":" + operand + "}}},\"variables\":{},\"features\":{"
                    + "\"subject\":{\"bucketBy\":\"userId\",\"conditions\":\"bad\",\"traffic\":[{\"key\":\"all\",\"conditions\":\"*\",\"percentage\":100000}]}}}"))
                .logLevel(FeaturevisorLogLevel.FATAL));
            try { assertFalse(f.isEnabled("subject"), operand); }
            finally { f.close(); }
        }
    }

    @Test void segmentReadsAfterACallbackUseTheAcceptedDatafile() throws Exception {
        Featurevisor[] holder = {null};
        DatafileContent update = data("4", Map.of("audience", Map.of("conditions", "*")), Map.of(), Map.of());
        FeaturevisorModule module = new FeaturevisorModule("segments").afterEvaluation((result, options) -> {
            if ("mutator".equals(options.getVariableKey())) assertTrue(holder[0].setDatafile(update));
            return result;
        });
        holder[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(data("4", Map.of("audience", Map.of("conditions", List.of())), Map.of(), Map.of(
                "mutator", Map.of("type", "boolean", "defaultValue", true),
                "subject", Map.of("type", "string", "defaultValue", "matched", "conditions", List.of(
                    Map.of("variable", "mutator", "operator", "equals", "value", true), "audience")))))
            .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
        try { assertEquals("matched", holder[0].getVariable("subject")); }
        finally { holder[0].close(); }
    }

    @Test void segmentDepthCountsTheTerminalWildcardAtTheExactBound() throws Exception {
        for (int length : List.of(255, 256, 257)) {
            Map<String, Object> segments = new java.util.LinkedHashMap<>();
            for (int index = 0; index < length; index++)
                segments.put("s" + index, Map.of("conditions", index + 1 == length ? "*" : "s" + (index + 1)));
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(data("4", segments, Map.of(), Map.of("subject", Map.of(
                    "type", "boolean", "defaultValue", true, "conditions", "s0"))))
                .logLevel(FeaturevisorLogLevel.FATAL));
            try {
                Evaluation result = f.evaluateVariable("subject");
                assertEquals(length > 256 ? "error" : "variable_default", result.getReason(), "depth " + length);
            } finally { f.close(); }
        }
    }

    @Test void attributeTerminalFastPredicatesDoNotConsumeAnActiveHandoff() throws Exception {
        Object attribute = Map.of("attribute", "country", "operator", "equals", "value", "nl");
        for (Object terminal : List.of(attribute, Map.of("and", List.of(attribute)))) {
            for (int length : List.of(256, 257, 258)) {
                Map<String, Object> segments = new java.util.LinkedHashMap<>();
                for (int index = 0; index < length; index++)
                    segments.put("s" + index, Map.of("conditions", index + 1 == length ? terminal : "s" + (index + 1)));
                Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(data("4", segments, Map.of(), Map.of("subject", Map.of(
                        "type", "boolean", "defaultValue", true, "conditions", "s0"))))
                    .logLevel(FeaturevisorLogLevel.FATAL));
                try {
                    assertEquals(length > 257 ? "error" : "variable_default",
                        f.evaluateVariable("subject", Map.of("country", "nl")).getReason(), "attribute depth " + length);
                } finally { f.close(); }
            }
        }
    }

    @Test void mixedGetterAndSegmentDepthKeepsTerminalFastPredicateSemantics() throws Exception {
        for (boolean attribute : List.of(false, true)) {
            for (int length : List.of(254, 255, 256)) {
                Map<String, Object> segments = new java.util.LinkedHashMap<>();
                segments.put("tail", Map.of("conditions", attribute
                    ? Map.of("attribute", "country", "operator", "equals", "value", "nl") : "*"));
                for (int index = 0; index < length; index++)
                    segments.put("s" + index, Map.of("conditions", index + 1 == length
                        ? Map.of("variable", "source", "operator", "equals", "value", true) : "s" + (index + 1)));
                Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(data("4", segments, Map.of(), Map.of(
                        "source", Map.of("type", "boolean", "defaultValue", true, "conditions", "tail"),
                        "subject", Map.of("type", "boolean", "defaultValue", true, "conditions", "s0"))))
                    .logLevel(FeaturevisorLogLevel.FATAL));
                try {
                    assertEquals(length > (attribute ? 255 : 254) ? "error" : "variable_default",
                        f.evaluateVariable("subject", Map.of("country", "nl")).getReason(), "mixed depth " + length + ", attribute=" + attribute);
                } finally { f.close(); }
            }
        }
    }

    @Test void featureStickySettersInBeforeHooksRefreshRootAndChildGetters() throws Exception {
        for (boolean childMode : List.of(false, true)) {
            Featurevisor[] holder = {null}; ChildInstance[] child = {null};
            FeaturevisorModule module = new FeaturevisorModule("sticky").beforeEvaluation(options -> {
                Map<String, Object> sticky = Map.of("subject", Map.of("enabled", true, "variation", "after", "variables", Map.of("value", 9)));
                if (childMode) child[0].setStickyFeatures(sticky, true);
                else holder[0].setStickyFeatures(sticky, true);
                return options;
            });
            holder[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(data("4", Map.of(), Map.of("subject", Map.of("bucketBy", "userId", "traffic", List.of(),
                    "variablesSchema", Map.of("value", Map.of("type", "integer", "defaultValue", 0)))), Map.of()))
                .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
            child[0] = holder[0].spawn();
            try {
                assertTrue(childMode ? child[0].isEnabled("subject") : holder[0].isEnabled("subject"));
                assertEquals("after", childMode ? child[0].getVariation("subject") : holder[0].getVariation("subject"));
                assertEquals(9, childMode ? child[0].getVariable("subject", "value") : holder[0].getVariable("subject", "value"));
            } finally { child[0].close(); holder[0].close(); }
        }
    }

    @Test void moduleAddRemoveAndUnsubscribeInvalidateCompletedSourceMemos() throws Exception {
        for (boolean unsubscribe : List.of(false, true)) {
            Featurevisor[] holder = {null}; Runnable[] remove = {null}; AtomicInteger reads = new AtomicInteger();
            FeaturevisorModule projection = new FeaturevisorModule("projection").afterEvaluation((result, options) ->
                "source".equals(options.getVariableKey()) ? result.variableValue(1) : result);
            FeaturevisorModule module = new FeaturevisorModule("changes").afterEvaluation((result, options) -> {
                if ("source".equals(options.getVariableKey())) reads.incrementAndGet();
                if ("add".equals(options.getVariableKey())) remove[0] = holder[0].addModule(projection);
                if ("remove".equals(options.getVariableKey())) {
                    if (unsubscribe) remove[0].run(); else holder[0].removeModule("projection");
                }
                return result;
            });
            List<Object> members = new ArrayList<>();
            for (String key : List.of("before", "add", "during", "remove", "after"))
                members.add(Map.of("key", key, "variable", key.equals("add") || key.equals("remove") ? key : "source"));
            holder[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(data("4", Map.of(), Map.of(), Map.of(
                    "source", Map.of("type", "integer", "defaultValue", 0),
                    "add", Map.of("type", "boolean", "defaultValue", true),
                    "remove", Map.of("type", "boolean", "defaultValue", true),
                    "subject", Map.of("compose", members))))
                .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
            try {
                assertEquals(Map.of("before", 0, "add", true, "during", 1, "remove", true, "after", 0), holder[0].getVariable("subject"));
                assertEquals(3, reads.get());
            } finally { holder[0].close(); }
        }
    }
}
