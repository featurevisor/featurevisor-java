package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModulePhaseSnapshotTest {
    private FeaturevisorModule module(String name, List<String> trace, Consumer<String> action) {
        return new FeaturevisorModule(name)
            .beforeEvaluation(options -> { trace.add(name + ":before"); action.accept("before"); return options; })
            .bucketKey(options -> { trace.add(name + ":key"); action.accept("key"); return options.getBucketKey(); })
            .bucketValue(options -> { trace.add(name + ":value"); action.accept("value"); return options.getBucketValue(); })
            .afterEvaluation((evaluation, options) -> { trace.add(name + ":after"); action.accept("after"); return evaluation; });
    }

    @Test void everyPhaseUsesStartingMembershipForFeatureAndGlobalEvaluations() {
        for (boolean global : new boolean[]{false, true}) {
            List<String> phases = global ? List.of("before", "after") : List.of("before", "key", "value", "after");
            for (String target : phases) for (String mode : List.of("add", "removeAdd", "remove")) {
                List<String> trace = new ArrayList<>();
                AtomicReference<Featurevisor> owner = new AtomicReference<>();
                AtomicBoolean changed = new AtomicBoolean();
                AtomicInteger closed = new AtomicInteger();
                FeaturevisorModule late = module("late", trace, phase -> {});
                FeaturevisorModule actor = module("actor", trace, phase -> {
                    if (!target.equals(phase) || !changed.compareAndSet(false, true)) return;
                    if (!mode.equals("add")) owner.get().removeModule("other");
                    if (!mode.equals("remove")) owner.get().addModule(late);
                });
                FeaturevisorModule other = module("other", trace, phase -> {}).close(closed::incrementAndGet);
                DatafileContent datafile = new ObjectMapper().convertValue(Map.of(
                    "schemaVersion", "4", "revision", "phase", "segments", Map.of(),
                    "features", Map.of("subject", Map.of("bucketBy", "userId", "traffic", List.of(
                        Map.of("key", "all", "conditions", "*", "percentage", 100000)))),
                    "variables", Map.of("subject", Map.of("type", "string", "defaultValue", "ok"))), DatafileContent.class);
                Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(datafile).modules(List.of(actor, other)).logLevel(FeaturevisorLogLevel.FATAL));
                owner.set(sdk);
                List<String> expected = new ArrayList<>();
                for (String phase : phases) {
                    boolean later = phases.indexOf(phase) > phases.indexOf(target);
                    expected.add("actor:" + phase);
                    if (!later || mode.equals("add")) expected.add("other:" + phase);
                    if (later && !mode.equals("remove")) expected.add("late:" + phase);
                }
                for (int pass = 0; pass < 2; pass++) {
                    Evaluation result = global ? sdk.evaluateVariable("subject") : sdk.evaluateFlag("subject", Map.of("userId", "u"));
                    if (global) assertEquals("ok", result.getVariableValue()); else assertTrue(result.getEnabled());
                    assertEquals(expected, trace, global + "/" + target + "/" + mode + "/" + pass);
                    assertEquals(mode.equals("add") ? 0 : 1, closed.get());
                    trace.clear();
                    expected.clear();
                    for (String phase : phases) {
                        expected.add("actor:" + phase);
                        if (mode.equals("add")) expected.add("other:" + phase);
                        if (!mode.equals("remove")) expected.add("late:" + phase);
                    }
                }
                sdk.close();
                assertEquals(1, closed.get());
            }
        }
    }

    @Test void phaseIterationReusesCopyOnWriteStorageIncludingWhenEmpty() {
        ModulesManager manager = new ModulesManager(new ModulesManager.ModulesManagerOptions());
        assertSame(manager.phaseModules(), manager.phaseModules());
        java.util.Iterator<FeaturevisorModule> empty = manager.phaseModules().iterator();
        FeaturevisorModule first = new FeaturevisorModule("first");
        manager.add(first);
        assertFalse(empty.hasNext());
        java.util.Iterator<FeaturevisorModule> installed = manager.phaseModules().iterator();
        manager.remove("first");
        assertSame(first, installed.next());
        assertFalse(installed.hasNext());
        assertFalse(manager.phaseModules().iterator().hasNext());
    }
}
