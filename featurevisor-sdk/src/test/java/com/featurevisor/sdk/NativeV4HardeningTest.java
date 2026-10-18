package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeV4HardeningTest {
    private DatafileContent datafile(String version, String revision, Map<String, Segment> segments,
            Map<String, Feature> features, Map<String, GlobalVariable> variables) {
        DatafileContent datafile = new DatafileContent(version, revision);
        datafile.setSegments(segments); datafile.setFeatures(features); datafile.setVariables(variables);
        return datafile;
    }
    private GlobalVariable variable(Object value) {
        GlobalVariable result = new GlobalVariable();
        result.setType(VariableType.OBJECT); result.setDefaultValue(value);
        return result;
    }
    private GlobalVariable alias(String key) {
        GlobalVariable result = new GlobalVariable(); result.setCompose(key); return result;
    }
    private Feature flag(Object conditions) {
        Feature result = new Feature(); result.setBucketBy(new Bucket("userId")); result.setConditions(conditions);
        Traffic rule = new Traffic("all"); rule.setConditions("*"); rule.setPercentage(100000);
        result.setTraffic(List.of(rule)); return result;
    }
    private Segment segment(Object conditions) { Segment result = new Segment(); result.setConditions(conditions); return result; }
    private Featurevisor instance(DatafileContent datafile, FeaturevisorModule... modules) {
        return Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().datafile(datafile)
            .logLevel(FeaturevisorLogLevel.FATAL).modules(List.of(modules)));
    }

    @Test void eagerAndLazyDiagnosticsStoreAndCacheBeforeReentrantCallbacks() {
        for (String version : List.of("2", "4")) {
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            Featurevisor[] f = {null};
            f[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostic -> {
                    if (!diagnostic.getCode().equals("conditions_parse_error")) return;
                    diagnostics.add(diagnostic);
                    assertEquals("accepted", f[0].getRevision());
                    Evaluation result = f[0].evaluateFlag("bad");
                    assertEquals("conditions_unmet", result.getReason());
                    assertEquals(Map.of("or", List.of()), result.getConditions());
                }));
            Feature bad = flag("{");
            DatafileContent incoming = datafile(version, "accepted", Map.of(), Map.of("bad", bad), Map.of());
            assertTrue(f[0].setDatafile(incoming));
            assertEquals(version.equals("2") ? 1 : 0, diagnostics.size());
            assertFalse(f[0].isEnabled("bad"));
            assertEquals(1, diagnostics.size());
            assertEquals(Set.of("conditions", "entityType", "entityKey"), diagnostics.get(0).getDetails().keySet());
            assertEquals("feature", diagnostics.get(0).getDetails().get("entityType"));
            assertEquals("{", bad.getConditions());
            assertTrue(f[0].setDatafile(datafile("4", "accepted", Map.of(), Map.of("other", flag("*")), Map.of())));
            assertFalse(f[0].isEnabled("bad")); assertEquals(1, diagnostics.size());
            assertTrue(f[0].setDatafile(incoming));
            assertFalse(f[0].isEnabled("bad")); assertEquals(2, diagnostics.size());
            assertTrue(f[0].setDatafile(incoming, true));
            assertFalse(f[0].isEnabled("bad")); assertEquals(3, diagnostics.size());
            f[0].close();
        }
    }

    @Test void stickyJsonAndGatedCompositionOwnershipUseOrdinaryGetterSemantics() {
        GlobalVariable json = variable("\"null\""); json.setType(VariableType.JSON);
        GlobalVariable subject = new GlobalVariable(); subject.setCompose(List.of(Map.of("key", "value", "variable", "json")));
        subject.setConditions(List.of()); Map<String, Object> disabled = new HashMap<>(Map.of("value", "off")); subject.setDisabledValue(disabled);
        GlobalVariable alias = alias("json"); alias.setType(VariableType.JSON);
        Featurevisor f = instance(datafile("4", "sticky", Map.of(), Map.of(), Map.of("json", json, "alias", alias, "subject", subject)));
        assertEquals("null", f.getVariable("alias"));
        f.setStickyVariables(Map.of("json", "\"sticky\""));
        assertEquals("sticky", f.getVariable("json")); assertEquals("sticky", f.getVariable("alias"));
        Object first = f.getVariable("subject"), second = f.getVariable("subject");
        assertEquals(disabled, first); assertNotSame(disabled, first); assertNotSame(first, second);
        f.close();
    }

    @Test void mixedSelectorsExposeFlattenedConditionsAndRetainEmptyOperands() {
        Object attribute = Map.of("attribute", "country", "operator", "equals", "value", "nl");
        Feature subject = flag(List.of(attribute)); subject.setRequiredFeatures(List.of("missing"));
        Force force = new Force(); force.setConditions(List.of(attribute)); force.setSegments(List.of("audience"));
        force.setRequiredFeatures(List.of("missing")); subject.setForce(List.of(force));
        Normalize.Result<Feature> normalized = Normalize.feature("subject", subject, false);
        assertEquals(List.of(attribute, Map.of("feature", "missing")), normalized.value.getConditions());
        assertEquals(List.of(attribute, "audience", Map.of("feature", "missing")), normalized.value.getForce().get(0).getConditions());
        subject.setConditions(List.of());
        Featurevisor f = instance(datafile("4", "empty", Map.of(), Map.of("subject", subject), Map.of()));
        assertEquals(List.of(Map.of("and", List.of()), Map.of("feature", "missing")), f.evaluateFlag("subject").getConditions());
        assertEquals(List.of(), subject.getConditions()); f.close();
    }

    @Test void concurrentReplacementsAndReentrantCallbacksPublishCompleteSnapshotsWithoutLocksHeld() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        Featurevisor f = instance(datafile("4", "start", Map.of(), Map.of("good", flag("*")), Map.of("source", variable("a"), "alias", alias("source"))));
        try {
            FeaturevisorUnsubscribe unsubscribe = f.on(FeaturevisorEventName.DATAFILE_SET, event -> {
                try { pool.submit(() -> f.setContext(Map.of("callback", true))).get(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue(f.setDatafile(datafile("4", "callback", Map.of(), Map.of(), Map.of())));
            unsubscribe.unsubscribe();
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            tasks.add(pool.submit(() -> {
                for (int index = 0; index < 100; index++) {
                    assertTrue(f.setDatafile(datafile("4", "revision" + index, Map.of(), Map.of("good", flag("*")),
                        Map.of("source", variable(index % 2 == 0 ? "a" : "b"), "alias", alias("source"))), true));
                }
            }));
            for (int reader = 0; reader < 3; reader++) tasks.add(pool.submit(() -> {
                for (int index = 0; index < 300; index++) {
                    assertTrue(f.isEnabled("good"));
                    assertTrue(List.of("a", "b").contains(f.getVariable("alias")));
                }
            }));
            for (java.util.concurrent.Future<?> task : tasks) task.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); f.close(); }
    }

    @Test void retainedUnreadLegacyAndV4DefinitionsKeepTheirEmptySemanticsAcrossMixedMerges() {
        Feature old = flag(null); old.getTraffic().get(0).setConditions(null); old.getTraffic().get(0).setSegments(List.of());
        Feature modern = flag(List.of());
        Featurevisor f = instance(datafile("2", "old", Map.of("old", segment(List.of())), Map.of("old", old), Map.of()));
        assertTrue(f.setDatafile(datafile("4", "new", Map.of("new", segment(List.of())), Map.of("new", modern), Map.of())));
        assertTrue(f.isEnabled("old")); assertFalse(f.isEnabled("new"));
        assertTrue(f.setDatafile(datafile("2", "later", Map.of(), Map.of("legacyRead", flag("old"), "modernRead", flag("new")), Map.of())));
        assertTrue(f.isEnabled("legacyRead")); assertFalse(f.isEnabled("modernRead"));
        assertEquals(List.of(), old.getTraffic().get(0).getSegments());
        assertTrue(f.setDatafile(datafile("4", "replacement", Map.of(), Map.of("new", flag("*")), Map.of()), true));
        assertFalse(f.isEnabled("old")); assertTrue(f.isEnabled("new")); f.close();
    }

    @Test void malformedSelectorsCannotBeInvertedThroughSegmentsAndEachSelectorReportsOnce() {
        List<FeaturevisorDiagnostic> errors = new ArrayList<>();
        Feature bad = flag(Map.of("not", List.of("badAlias")));
        Force force = new Force(); force.setConditions("{"); force.setSegments("{"); force.setEnabled(true);
        bad.setForce(List.of(force));
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile("4", "errors", Map.of("badAlias", segment("bad"), "bad", segment("{")),
                Map.of("bad", bad, "good", flag("*")), Map.of()))
            .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(errors::add));
        assertTrue(errors.isEmpty());
        assertEquals("conditions_unmet", f.evaluateFlag("bad").getReason());
        assertFalse(f.isEnabled("bad")); assertTrue(f.isEnabled("good"));
        assertEquals(3, errors.stream().filter(d -> d.getCode().equals("conditions_parse_error")).count());
        f.close();
    }

    @Test void borrowedCompositionsDoNotTraverseSourceValuesForMemoization() {
        Map<String, Object> borrowed = new AbstractMap<>() {
            @Override public Set<Entry<String, Object>> entrySet() { throw new AssertionError("borrowed value traversed"); }
        };
        AtomicInteger reads = new AtomicInteger();
        GlobalVariable subject = new GlobalVariable();
        subject.setCompose(List.of(Map.of("key", "first", "variable", "source"), Map.of("key", "second", "variable", "source")));
        FeaturevisorModule module = new FeaturevisorModule("borrowed").afterEvaluation((evaluation, options) -> {
            if ("source".equals(options.getVariableKey())) { reads.incrementAndGet(); return evaluation.variableValue(borrowed); }
            return evaluation;
        });
        Featurevisor f = instance(datafile("4", "borrowed", Map.of(), Map.of(), Map.of("subject", subject, "source", variable(null))), module);
        Map<?, ?> result = (Map<?, ?>) f.getVariable("subject");
        assertSame(borrowed, result.get("first")); assertSame(borrowed, result.get("second")); assertEquals(1, reads.get());
        f.getVariable("subject"); assertEquals(2, reads.get()); f.close();
    }

    @Test void conditionSnapshotsAreOwnedAndPurposeMemoizationDoesNotMixWithComposition() {
        Map<String, Object> source = new HashMap<>(Map.of("enabled", true));
        List<String> purposes = new ArrayList<>();
        List<EvaluateOptions> retained = new ArrayList<>();
        GlobalVariable subject = new GlobalVariable();
        Object condition = Map.of("variable", "source", "path", List.of("enabled"), "operator", "equals", "value", true);
        subject.setConditions(condition);
        subject.setCompose(List.of(Map.of("key", "first", "variable", "source"), Map.of("key", "second", "variable", "source")));
        VariableOverride override = new VariableOverride(); override.setConditions(condition); override.setKey("snapshot");
        VariableMutationOperation operation = new VariableMutationOperation(); operation.setOp("set"); operation.setPath(List.of()); operation.setValue(Map.of("snapshot", true));
        override.setOperations(List.of(operation)); override.setOnMatch(1); override.setOnMiss(1);
        subject.setOverrideProgram(List.of(override));
        FeaturevisorModule module = new FeaturevisorModule("purposes").beforeEvaluation(options -> {
            retained.add(options);
            if ("source".equals(options.getVariableKey())) {
                purposes.add(options.getDependencyPurpose());
                assertEquals("subject", options.getDependencyOf().getVariableKey());
                if ("composition".equals(options.getDependencyPurpose())) source.put("enabled", false);
            } else assertNull(options.getDependencyPurpose());
            return options;
        });
        Featurevisor f = instance(datafile("4", "purposes", Map.of(), Map.of(), Map.of("subject", subject, "source", variable(source))), module);
        Evaluation result = f.evaluateVariable("subject");
        assertEquals("variable_override_rule", result.getReason()); assertEquals(Map.of("snapshot", true), result.getVariableValue());
        assertEquals(List.of("condition", "composition"), purposes);
        retained.forEach(options -> assertNull(options.getStickySource()));
        assertFalse((Boolean) source.get("enabled")); f.close();
    }

    @Test void mixedCyclesAndDepthErrorsLeaveSubsequentCallsClean() {
        for (boolean cycle : List.of(false, true)) {
            Map<String, GlobalVariable> variables = new LinkedHashMap<>();
            for (int index = 0; index < 300; index++) variables.put("v" + index, alias("v" + (index + 1)));
            variables.put("v300", cycle ? alias("v0") : variable("end"));
            GlobalVariable mixed = alias("v0"); mixed.setConditions("segment"); variables.put("mixed", mixed);
            Featurevisor f = instance(datafile("4", "depth", Map.of("segment", segment(Map.of("feature", "gate"))),
                Map.of("gate", flag(Map.of("variable", "mixed", "operator", "notNull")), "good", flag("*")), variables));
            assertEquals("error", f.evaluateVariable("v0").getReason());
            assertEquals("error", f.evaluateVariable("mixed").getReason());
            assertTrue(f.isEnabled("good")); assertEquals("variable_not_found", f.evaluateVariable("missing").getReason()); f.close();
        }
    }

    @Test void compositionPreservesChildStickyStateAndEffectiveModuleContext() {
        GlobalVariable subject = new GlobalVariable(); subject.setCompose(List.of(
            Map.of("key", "feature", "feature", "flag"), Map.of("key", "value", "variable", "source")));
        GlobalVariable source = variable("default");
        VariableOverride override = new VariableOverride("regional"); override.setConditions(Map.of("attribute", "country", "operator", "equals", "value", "nl")); source.setOverrides(List.of(override));
        FeaturevisorModule module = new FeaturevisorModule("context").beforeEvaluation(options -> {
            if (options.getDependencyPurpose() == null) return options.copy().context(Map.of("country", "nl"));
            assertEquals(Map.of("country", "nl"), options.getContext()); return options;
        });
        Featurevisor f = instance(datafile("4", "child", Map.of(), Map.of("flag", flag("*")), Map.of("subject", subject, "source", source)), module);
        f.setContext(Map.of("country", "us", "unrelated", true));
        ChildInstance child = f.spawn(Map.of("country", "de"), new Featurevisor.SpawnOptions()
            .stickyFeatures(Map.of("flag", Map.of("enabled", false))).stickyVariables(Map.of("source", "child")));
        assertEquals(Map.of("feature", false, "value", "child"), child.getVariable("subject"));
        assertEquals(Map.of("feature", true, "value", "regional"), f.getVariable("subject"));
        child.setStickyVariables(Map.of("subject", Map.of("whole", true)));
        assertEquals(Map.of("whole", true), child.getVariable("subject")); child.close(); f.close();
    }

    @Test void updateIndexTraversesMixedRawConditionsAndCompositionWithoutDiagnostics() {
        GlobalVariable source = variable(true); source.setHash("before");
        GlobalVariable composed = alias("source"); composed.setHash("alias");
        GlobalVariable consumer = variable("yes"); consumer.setHash("consumer"); consumer.setConditions("middle");
        Feature subject = flag("middle"); subject.setHash("feature");
        Featurevisor f = instance(datafile("4", "before", Map.of("middle", segment("{\"variable\":\"alias\",\"operator\":\"equals\",\"value\":true}"), "bad", segment("{")),
            Map.of("subject", subject), Map.of("source", source, "alias", composed, "consumer", consumer)));
        List<FeaturevisorEventDetails> events = new ArrayList<>(); List<FeaturevisorDiagnostic> errors = new ArrayList<>();
        f.on(FeaturevisorEventName.DATAFILE_SET, events::add);
        FeaturevisorModule observer = new FeaturevisorModule("diagnostics").setup(api -> api.onDiagnostic(errors::add)); f.addModule(observer);
        GlobalVariable next = variable(false); next.setHash("after");
        assertTrue(f.setDatafile(datafile("4", "after", Map.of(), Map.of(), Map.of("source", next))));
        assertTrue(((List<?>) events.get(0).get("features")).contains("subject"));
        assertTrue(((List<?>) events.get(0).get("variables")).containsAll(List.of("source", "alias", "consumer")));
        assertTrue(errors.stream().noneMatch(d -> d.getCode().equals("conditions_parse_error")));
        assertFalse(f.isEnabled("subject"));
        assertTrue(f.setDatafile(datafile("4", "removed", Map.of("middle", segment("*")), Map.of("subject", subject), Map.of("consumer", consumer)), true));
        assertTrue(((List<?>) events.get(1).get("variables")).containsAll(List.of("source", "alias", "consumer"))); f.close();
    }
}
