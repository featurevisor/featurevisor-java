package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EvaluationStateLifecycleTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DatafileContent datafile(Map<String, Object> features, Map<String, Object> variables, Map<String, Object> segments) {
        return JSON.convertValue(Map.of("schemaVersion", "4", "revision", "evaluation-state",
            "features", features, "variables", variables, "segments", segments), DatafileContent.class);
    }

    private InstanceEvaluationDataProvider provider(DatafileContent raw) {
        return new InstanceEvaluationDataProvider(new InstanceEvaluationDataProvider.InstanceEvaluationDataProviderOptions()
            .datafile(raw).diagnostics(DiagnosticReporter.createDiagnosticReporter(
                new DiagnosticReporter.DiagnosticReporterOptions().handler((level, message, details) -> fail("index emitted a diagnostic")))));
    }

    @Test void retainedSelectorsAreNeverExtractedDuringCachedUpdates() {
        for (int size : new int[]{8, 512}) {
            Map<String, Object> segments = new HashMap<>();
            for (int index = 0; index < size; index++) segments.put("s" + index,
                Map.of("conditions", "[{\"variable\":\"gate\",\"operator\":\"equals\",\"value\":true}]"));
            DatafileContent raw = datafile(Map.of("subject", Map.of("hash", "subject", "conditions", "s0", "bucketBy", "userId", "traffic", List.of())),
                Map.of("gate", Map.of("hash", "gate0", "type", "boolean", "defaultValue", true),
                    "alias", Map.of("hash", "alias", "type", "boolean", "compose", "gate")), segments);
            InstanceEvaluationDataProvider current = provider(raw);
            assertEquals(0, current.getCachedDependencyIndexCount());
            for (int revision = 1; revision <= 8; revision++) {
                DatafileContent incoming = datafile(Map.of(), Map.of("gate", Map.of("hash", "gate" + revision,
                    "type", "boolean", "defaultValue", revision % 2 == 0)), Map.of());
                DatafileContent nextRaw = current.getDatafile();
                nextRaw.setVariables(new HashMap<>(nextRaw.getVariables()));
                nextRaw.getVariables().put("gate", incoming.getVariables().get("gate"));
                InstanceEvaluationDataProvider next = provider(nextRaw);
                next.retainCaches(current, incoming);
                FeaturevisorEventDetails event = Events.getParamsForProviderSetEvent(current, next, false);
                assertEquals(Set.of("subject"), new HashSet<>((List<?>) event.get("features")));
                assertEquals(Set.of("alias", "gate"), new HashSet<>((List<?>) event.get("variables")));
                assertEquals(1, next.getDependencyIndex().extractedEntities, "size=" + size + ", revision=" + revision);
                assertEquals(1, current.getCachedDependencyIndexCount());
                current = next;
            }
            current.clearCaches();
            assertEquals(0, current.getCachedDependencyIndexCount());
        }
    }

    @Test void initialNotificationLeavesBothIndexesCold() {
        InstanceEvaluationDataProvider empty = provider(datafile(Map.of(), Map.of(), Map.of()));
        InstanceEvaluationDataProvider next = provider(datafile(Map.of(),
            Map.of("gate", Map.of("hash", "gate", "type", "boolean", "defaultValue", true)), Map.of()));
        assertEquals(List.of("gate"), Events.getParamsForProviderSetEvent(empty, next, true).get("variables"));
        assertEquals(0, empty.getCachedDependencyIndexCount());
        assertEquals(0, next.getCachedDependencyIndexCount());
    }

    @Test void concurrentPartialWritersKeepAllEntitiesAndRetainedDiagnosticCaches() throws Exception {
        java.util.concurrent.atomic.AtomicInteger diagnostics = new java.util.concurrent.atomic.AtomicInteger();
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile(Map.of(), Map.of(
                "source", Map.of("hash", "source", "type", "string", "defaultValue", "stable"),
                "alias", Map.of("hash", "alias", "type", "string", "compose", "source"),
                "broken", Map.of("hash", "broken", "type", "string", "defaultValue", "wrong", "conditions", "[broken")), Map.of()))
            .logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostic -> {
                if ("conditions_parse_error".equals(diagnostic.getCode())) diagnostics.incrementAndGet();
            }));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            assertNull(sdk.getVariable("broken"));
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < 128; index++) {
                final int key = index;
                tasks.add(pool.submit(() -> {
                    if (key % 4 == 0) assertTrue(sdk.setDatafile(datafile(Map.of(), Map.of("v" + key,
                        Map.of("hash", "v" + key, "type", "integer", "defaultValue", key)), Map.of())));
                    else {
                        assertEquals("stable", sdk.getVariable("alias"));
                        assertNull(sdk.getVariable("broken"));
                    }
                }));
            }
            for (java.util.concurrent.Future<?> task : tasks) task.get(30, java.util.concurrent.TimeUnit.SECONDS);
            for (int index = 0; index < 128; index += 4) assertEquals(index, sdk.getVariable("v" + index));
            assertEquals(1, diagnostics.get());
        } finally { pool.shutdownNow(); sdk.close(); }
    }

    @Test void oldAndNewGraphsRemainIndependentAndRemovedOwnerEdgesDisappear() {
        DatafileContent before = datafile(Map.of(), Map.of(
            "root", Map.of("hash", "root", "type", "boolean", "defaultValue", true),
            "bridge", Map.of("hash", "bridge", "type", "boolean", "defaultValue", true),
            "phantom", Map.of("hash", "phantom", "type", "boolean", "compose", "bridge")), Map.of());
        DatafileContent after = datafile(Map.of(), Map.of(
            "root", Map.of("hash", "root2", "type", "boolean", "defaultValue", false),
            "bridge", Map.of("hash", "bridge2", "type", "boolean", "compose", "root")), Map.of());
        Dependencies.Index old = Dependencies.create(before);
        Dependencies.Index next = Dependencies.update(old, before, after);
        Set<String> features = new HashSet<>(), variables = new HashSet<>(Set.of("root"));
        Dependencies.addChanges(features, variables, Set.of(), old);
        Dependencies.addChanges(features, variables, Set.of(), next);
        assertEquals(Set.of("root", "bridge"), variables);
        assertEquals(Set.of("1:phantom"), old.dependents[1].get("bridge"));
        assertNull(next.dependents[1].get("bridge"));
        assertFalse(next.owners.containsKey("1:phantom"));
    }

    @Test void currentPhaseModulesIncludeLateAdditionsAndExcludeRemovalsForFeatureAndGlobalRoots() {
        for (boolean global : new boolean[]{false, true}) for (boolean removing : new boolean[]{false, true})
                for (boolean disableGate : new boolean[]{false, true}) {
            List<String> trace = new ArrayList<>();
            AtomicReference<Featurevisor> owner = new AtomicReference<>();
            FeaturevisorModule observer = new FeaturevisorModule("observer").beforeEvaluation(options -> {
                trace.add("before:" + (options.isGlobalVariable() ? options.getVariableKey() : options.getFeatureKey()));
                return options;
            }).afterEvaluation((evaluation, options) -> {
                trace.add("after:" + (options.isGlobalVariable() ? options.getVariableKey() : options.getFeatureKey()));
                if (disableGate && "gate".equals(options.getFeatureKey())) evaluation.enabled(false);
                return evaluation;
            });
            Object conditions = Map.of("or", List.of(Map.of("feature", "broken"), Map.of("feature", "gate")));
            Map<String, Object> enabled = Map.of("bucketBy", "userId", "traffic", List.of(
                Map.of("key", "all", "conditions", "*", "percentage", 100000)));
            Map<String, Object> subject = new HashMap<>(enabled);
            subject.put("conditions", conditions);
            Map<String, Object> broken = new HashMap<>(enabled);
            broken.put("conditions", "[broken");
            Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile(Map.of("broken", broken, "gate", enabled, "subject", subject),
                    Map.of("subject", Map.of("type", "string", "defaultValue", "matched", "conditions", conditions)), Map.of()))
                .logLevel(FeaturevisorLogLevel.ERROR).modules(removing ? List.of(observer) : List.of())
                .onDiagnostic(diagnostic -> {
                    if (!"conditions_parse_error".equals(diagnostic.getCode())) return;
                    if (removing) owner.get().removeModule("observer");
                    else owner.get().addModule(observer);
                }));
            owner.set(sdk);
            boolean blocked = disableGate && !removing;
            Evaluation result = global ? sdk.evaluateVariable("subject") : sdk.evaluateFlag("subject", Map.of("userId", "u"));
            if (global) assertEquals(blocked ? null : "matched", result.getVariableValue());
            else assertEquals(!blocked, result.getEnabled());
            if (blocked) assertEquals("conditions_unmet", result.getReason());
            assertEquals(removing ? List.of("before:subject", "before:broken")
                : List.of("after:broken", "before:gate", "after:gate", "after:subject"), trace);
            sdk.close();
        }
    }
}
