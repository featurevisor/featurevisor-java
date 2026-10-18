package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class EvaluationSessionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final class VisitedMap extends HashMap<String, Object> {
        private final AtomicInteger visits;
        VisitedMap(AtomicInteger visits) {
            this.visits = visits;
            put("nested", Map.of("value", 1));
        }
        @Override public java.util.Set<Map.Entry<String, Object>> entrySet() {
            visits.incrementAndGet();
            return super.entrySet();
        }
    }

    @Test void unrelatedStickyEntriesAreNotVisitedOnPrerequisiteReads() throws Exception {
        Map<String, Object> enabled = Map.of("bucketBy", "userId", "traffic",
            List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)));
        Map<String, Object> rootFeature = new HashMap<>(enabled);
        rootFeature.put("requiredFeatures", List.of("leaf", "leaf"));
        for (String kind : List.of("features", "variables")) {
            for (boolean childMode : new boolean[] {false, true}) {
                for (int size : new int[] {0, 100, 1000, 5000}) {
                    AtomicInteger visits = new AtomicInteger();
                    AtomicInteger leafReads = new AtomicInteger();
                    Map<String, Object> sticky = new HashMap<>();
                    for (int index = 0; index < size; index++) {
                        Object value = new VisitedMap(visits);
                        sticky.put("unrelated" + index, kind.equals("features")
                            ? Map.of("enabled", true, "variables", Map.of("payload", value)) : value);
                    }
                    sticky.put("leaf", kind.equals("features") ? Map.of("enabled", true) : 1);
                    FeaturevisorModule module = new FeaturevisorModule("count").beforeEvaluation(options -> {
                        if ("leaf".equals(options.getFeatureKey()) || "leaf".equals(options.getVariableKey())) {
                            leafReads.incrementAndGet();
                        }
                        return options;
                    });
                    Featurevisor sdk = instance(kind.equals("features")
                            ? Map.of("root", rootFeature, "leaf", enabled) : Map.of(),
                        kind.equals("variables") ? Map.of("root", variable(1,
                            List.of(requirement("leaf", 1), requirement("leaf", 1))), "leaf", variable(0, null)) : Map.of(),
                        module);
                    ChildInstance child = null;
                    try {
                        if (!childMode) {
                            if (kind.equals("features")) sdk.setStickyFeatures(sticky, true);
                            else sdk.setStickyVariables(sticky, true);
                        }
                        child = childMode ? sdk.spawn(Map.of(), kind.equals("features")
                            ? new Featurevisor.SpawnOptions().stickyFeatures(sticky)
                            : new Featurevisor.SpawnOptions().stickyVariables(sticky)) : null;
                        visits.set(0); // Handoff and event generation are outside the read.
                        if (kind.equals("features")) {
                            assertTrue(childMode ? child.isEnabled("root") : sdk.isEnabled("root"));
                        } else {
                            assertEquals(1, childMode ? child.getVariable("root") : sdk.getVariable("root"));
                        }
                        assertEquals(0, visits.get(), kind + ", child=" + childMode + ", size=" + size);
                        assertEquals(1, leafReads.get(), kind + ", child=" + childMode + ", size=" + size);
                    } finally {
                        if (child != null) child.close();
                        sdk.close();
                    }
                }
            }
        }
    }

    @Test void stickyScalingBenchmarkForSerialRuns() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("1".equals(System.getenv("FEATUREVISOR_STICKY_BENCHMARK")));
        int warmup = benchmarkCount("FEATUREVISOR_STICKY_WARMUP", 5000);
        int iterations = benchmarkCount("FEATUREVISOR_STICKY_ITERATIONS", 10000);
        System.out.println("kind,size,instance,nanoseconds_per_evaluation");
        Map<String, Object> enabled = Map.of("bucketBy", "userId", "traffic",
            List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)));
        for (String kind : List.of("features", "variables")) {
            for (int size : new int[] {0, 100, 1000, 5000}) {
                Map<String, Object> sticky = exportedSticky(kind, size, enabled);
                Map<String, Object> root = variable(1, kind.equals("features") ? null
                    : List.of(requirement("leaf", 1), requirement("leaf", 1)));
                if (kind.equals("features")) root.put("requiredFeatures", List.of("leaf", "leaf"));
                for (boolean childMode : new boolean[] {false, true}) {
                    Featurevisor sdk = instance(kind.equals("features") ? Map.of("leaf", enabled) : Map.of(),
                        kind.equals("features") ? Map.of("root", root)
                            : Map.of("root", root, "leaf", variable(0, null)));
                    ChildInstance child = null;
                    try {
                        if (!childMode) {
                            if (kind.equals("features")) sdk.setStickyFeatures(sticky, true);
                            else sdk.setStickyVariables(sticky, true);
                        }
                        child = childMode ? sdk.spawn(Map.of(), kind.equals("features")
                            ? new Featurevisor.SpawnOptions().stickyFeatures(sticky)
                            : new Featurevisor.SpawnOptions().stickyVariables(sticky)) : null;
                        assertEquals(1, childMode ? child.getVariable("root") : sdk.getVariable("root"));
                        for (int index = 0; index < warmup; index++) {
                            if (childMode) child.getVariable("root"); else sdk.getVariable("root");
                        }
                        long start = System.nanoTime();
                        for (int index = 0; index < iterations; index++) {
                            if (childMode) child.getVariable("root"); else sdk.getVariable("root");
                        }
                        System.out.println(kind + "," + size + "," + (childMode ? "child" : "primary")
                            + "," + (System.nanoTime() - start) / iterations);
                    } finally {
                        if (child != null) child.close();
                        sdk.close();
                    }
                }
            }
        }
    }

    @Test void exportedStickyCorpusWorksForPrimaryAndChild() throws Exception {
        Map<String, Object> enabled = Map.of("bucketBy", "userId", "traffic",
            List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)));
        for (String kind : List.of("features", "variables")) {
            Map<String, Object> sticky = exportedSticky(kind, 100, enabled);
            assertEquals(101, sticky.size());
            Map<String, Object> root = variable(1, kind.equals("features") ? null
                : List.of(requirement("leaf", 1), requirement("leaf", 1)));
            if (kind.equals("features")) root.put("requiredFeatures", List.of("leaf", "leaf"));
            Featurevisor sdk = instance(kind.equals("features") ? Map.of("leaf", enabled) : Map.of(),
                kind.equals("features") ? Map.of("root", root) : Map.of("root", root, "leaf", variable(0, null)));
            ChildInstance child = null;
            try {
                if (kind.equals("features")) sdk.setStickyFeatures(sticky, true);
                else sdk.setStickyVariables(sticky, true);
                assertEquals(1, sdk.getVariable("root"));
                child = sdk.spawn(Map.of(), kind.equals("features")
                    ? new Featurevisor.SpawnOptions().stickyFeatures(sticky)
                    : new Featurevisor.SpawnOptions().stickyVariables(sticky));
                assertEquals(1, child.getVariable("root"));
            } finally {
                if (child != null) child.close();
                sdk.close();
            }
        }
    }

    private Map<String, Object> exportedSticky(String kind, int size, Map<String, Object> enabled) throws Exception {
        Map<String, Object> features = new HashMap<>();
        Map<String, Object> variables = new HashMap<>();
        for (int index = 0; index < size; index++) {
            if (kind.equals("features")) features.put("unrelated" + index, enabled);
            else variables.put("unrelated" + index, variable(index, null));
        }
        if (kind.equals("features")) features.put("leaf", enabled);
        else variables.put("leaf", variable(1, null));
        Featurevisor exporter = instance(features, variables);
        try {
            if (kind.equals("features")) {
                // Java exports typed feature objects; convert once at handoff.
                @SuppressWarnings("unchecked") Map<String, Object> converted =
                    mapper.convertValue(exporter.getFeatureEvaluations(Map.of("userId", "bench")).getValue(), Map.class);
                return converted;
            }
            return exporter.getVariableEvaluations();
        } finally {
            exporter.close();
        }
    }

    private static int benchmarkCount(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null) return fallback;
        try {
            int value = Integer.parseInt(raw);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // Report the same invalid setting for nonnumeric and nonpositive input.
        }
        throw new IllegalArgumentException(name + " must be a positive integer");
    }

    private Featurevisor instance(Map<String, Object> features, Map<String, Object> variables, FeaturevisorModule... modules) throws Exception {
        Map<String, Object> datafile = new LinkedHashMap<>();
        datafile.put("schemaVersion", "2");
        datafile.put("revision", "session");
        datafile.put("segments", Map.of());
        datafile.put("features", features);
        datafile.put("variables", variables);
        return Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson(mapper.writeValueAsString(datafile)))
            .modules(List.of(modules)).logLevel(FeaturevisorLogLevel.FATAL));
    }

    private Map<String, Object> variable(Object value, Object requirements) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "integer");
        result.put("defaultValue", value);
        if (requirements != null) result.put("conditions", requirements);
        return result;
    }

    private Map<String, Object> requirement(String key, Object value) {
        return Map.of("variable", key, "operator", "equals", "value", value);
    }

    @Test void variableAndFeatureDiamondsStayBoundedAndExposeImmediateParent() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("leaf", variable(1, null));
        String previous = "leaf";
        for (int index = 0; index < 18; index++) {
            String next = "level" + index;
            variables.put(next, variable(1, List.of(requirement(previous, 1), requirement(previous, 1))));
            previous = next;
        }
        List<DependencyOf> parents = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("observe").beforeEvaluation(options -> {
            reads.incrementAndGet();
            parents.add(options.getDependencyOf());
            return options;
        });
        Featurevisor sdk = instance(Map.of(), variables, module);
        assertEquals(1, sdk.getVariable(previous));
        assertEquals(19, reads.get());
        assertNull(parents.get(0));
        assertEquals(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "level17"), parents.get(1));
        assertEquals(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "level0"), parents.get(18));
        assertEquals(1, sdk.getVariable(previous));
        assertEquals(38, reads.get());

        Map<String, Object> features = new LinkedHashMap<>();
        Map<String, Object> enabled = Map.of("bucketBy", "userId", "traffic", List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)));
        features.put("leaf", enabled);
        previous = "leaf";
        for (int index = 0; index < 18; index++) {
            String next = "level" + index;
            Map<String, Object> feature = new HashMap<>(enabled);
            feature.put("requiredFeatures", List.of(previous, previous));
            features.put(next, feature);
            previous = next;
        }
        reads.set(0);
        assertTrue(instance(features, Map.of(), module).isEnabled(previous));
        assertEquals(19, reads.get());
    }

    @Test void effectiveContextAndIdentityKeepSeparateDependencyReads() throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("leaf", variable(0, null));
        variables.put("a", variable(1, List.of(requirement("leaf", 1))));
        variables.put("b", variable(1, List.of(requirement("leaf", 2))));
        variables.put("root", variable(1, List.of(requirement("a", 1), requirement("b", 1))));
        AtomicInteger leaves = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("contexts")
            .beforeEvaluation(options -> {
                if (!"a".equals(options.getVariableKey()) && !"b".equals(options.getVariableKey())) return options;
                Map<String, Object> context = new HashMap<>(options.getContext());
                context.put("number", "a".equals(options.getVariableKey()) ? 1 : 2);
                return options.copy().context(context);
            })
            .afterEvaluation((evaluation, options) -> {
                if ("leaf".equals(evaluation.getVariableKey())) {
                    leaves.incrementAndGet();
                    evaluation.setVariableValue(options.getContext().get("number"));
                }
                return evaluation;
            });
        assertEquals(1, instance(Map.of(), variables, module).getVariable("root"));
        assertEquals(2, leaves.get());
    }

    @Test void twoNodePerLevelGraphStaysBoundedByEdges() throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("leafA", variable(1, null));
        variables.put("leafB", variable(1, null));
        String left = "leafA";
        String right = "leafB";
        for (int index = 0; index < 18; index++) {
            String nextLeft = "a" + index;
            String nextRight = "b" + index;
            List<Object> requirements = List.of(requirement(left, 1), requirement(right, 1));
            variables.put(nextLeft, variable(1, requirements));
            variables.put(nextRight, variable(1, requirements));
            left = nextLeft;
            right = nextRight;
        }
        variables.put("root", variable(1, List.of(requirement(left, 1), requirement(right, 1))));
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("bounded")
            .beforeEvaluation(options -> { before.incrementAndGet(); return options; })
            .afterEvaluation((evaluation, options) -> { after.incrementAndGet(); return evaluation; });
        assertEquals(1, instance(Map.of(), variables, module).getVariable("root"));
        assertEquals(75, before.get());
        assertEquals(75, after.get());
    }

    @Test void rootHookContextReplacementIsInheritedWithoutInstanceRemerge() throws Exception {
        Map<String, Object> variables = Map.of(
            "leaf", variable(0, null),
            "root", variable(1, List.of(requirement("leaf", 1))));
        Map<String, Object> datafile = Map.of("schemaVersion", "2", "revision", "context", "segments", Map.of(),
            "features", Map.of(), "variables", variables);
        FeaturevisorModule module = new FeaturevisorModule("replace-context")
            .beforeEvaluation(options -> "root".equals(options.getVariableKey()) ? options.copy().context(Map.of()) : options)
            .afterEvaluation((evaluation, options) -> {
                if ("leaf".equals(evaluation.getVariableKey())) {
                    evaluation.setVariableValue(options.getContext().containsKey("country") ? 2 : 1);
                }
                return evaluation;
            });
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson(mapper.writeValueAsString(datafile)))
            .context(Map.of("country", "de")).modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
        assertEquals(1, sdk.getVariable("root"));
    }

    @Test void knownStickyFeatureMapRetainsDiamondMemo() throws Exception {
        Map<String, Object> variables = Map.of(
            "leaf", variable(1, null),
            "root", variable(1, List.of(requirement("leaf", 1), requirement("leaf", 1))));
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("count").beforeEvaluation(options -> {
            if ("leaf".equals(options.getVariableKey())) reads.incrementAndGet();
            return options;
        });
        Featurevisor sdk = instance(Map.of(), variables, module);
        sdk.setStickyFeatures(Map.of("unrelated", Map.of("enabled", true, "variables", Map.of("nested", Map.of("number", 1)))));
        assertEquals(1, sdk.getVariable("root"));
        assertEquals(1, reads.get());
    }

    @Test void requesterAwareMemoSeparatesHookResultsButSharesRepeatedEdges() throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("leaf", variable(0, null));
        variables.put("a", variable(1, List.of(requirement("leaf", 1), requirement("leaf", 1))));
        variables.put("b", variable(1, List.of(requirement("leaf", 2), requirement("leaf", 2))));
        variables.put("root", variable(1, List.of(requirement("a", 1), requirement("b", 1))));
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("requester").afterEvaluation((evaluation, options) -> {
            if ("leaf".equals(evaluation.getVariableKey())) {
                reads.incrementAndGet();
                evaluation.setVariableValue("a".equals(options.getDependencyOf().getVariableKey()) ? 1 : 2);
            }
            return evaluation;
        });
        assertEquals(1, instance(Map.of(), variables, module).getVariable("root"));
        assertEquals(2, reads.get());
    }

    @Test void moduleMetadataDistinguishesFlagVariationFeatureAndGlobalVariables() throws Exception {
        Map<String, Object> source = Map.of(
            "bucketBy", "userId",
            "variations", List.of(Map.of("value", "beta", "variables", Map.of("count", 1))),
            "variablesSchema", Map.of("count", Map.of("type", "integer", "defaultValue", 1)),
            "traffic", List.of(Map.of("key", "all", "segments", "*", "percentage", 100000, "variation", "beta")));
        Map<String, Object> root = variable(1, List.of(
            Map.of("feature", "source", "variable", "count", "operator", "equals", "value", 1),
            requirement("global", 1)));
        root.put("requiredFeatures", List.of(Map.of("feature", "source", "enabled", true, "variation", "beta")));
        List<DependencyOf> kinds = new ArrayList<>();
        List<DependencyOf> parents = new ArrayList<>();
        FeaturevisorModule module = new FeaturevisorModule("metadata").beforeEvaluation(options -> {
            kinds.add(DependencyOf.from(options));
            parents.add(options.getDependencyOf());
            return options;
        });
        assertEquals(1, instance(Map.of("source", source), Map.of("global", variable(1, null), "root", root), module).getVariable("root"));
        assertNull(parents.get(0));
        assertEquals(5, kinds.size());
        assertTrue(kinds.subList(1, kinds.size()).containsAll(List.of(
            new DependencyOf(Evaluation.TYPE_FLAG, "source", null),
            new DependencyOf(Evaluation.TYPE_VARIATION, "source", null),
            new DependencyOf(Evaluation.TYPE_VARIABLE, "source", "count"),
            new DependencyOf(Evaluation.TYPE_VARIABLE, null, "global"))));
        assertTrue(parents.subList(1, parents.size()).stream().allMatch(value ->
            new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root").equals(value)));
    }

    @Test void replacementHooksCannotEraseRequesterAndRedirectedRootStillCycles() throws Exception {
        Map<String, Object> variables = Map.of(
            "leaf", variable(1, null),
            "root", variable(1, List.of(requirement("leaf", 1))));
        List<DependencyOf> seen = new ArrayList<>();
        FeaturevisorModule replace = new FeaturevisorModule("replace").beforeEvaluation(options ->
            options.copy().dependencyOf(null));
        FeaturevisorModule observe = new FeaturevisorModule("observe").beforeEvaluation(options -> {
            if ("leaf".equals(options.getVariableKey())) seen.add(options.getDependencyOf());
            return options;
        });
        assertEquals(1, instance(Map.of(), variables, replace, observe).getVariable("root"));
        assertEquals(List.of(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root")), seen);

        AtomicInteger originalReads = new AtomicInteger();
        FeaturevisorModule redirect = new FeaturevisorModule("redirect").beforeEvaluation(options -> {
            if ("a".equals(options.getVariableKey())) {
                originalReads.incrementAndGet();
                return options.copy().variableKey("b");
            }
            return options;
        });
        Featurevisor cyclic = instance(Map.of(), Map.of(
            "a", variable(1, null),
            "b", variable(1, List.of(requirement("a", 1)))
        ), redirect);
        assertEquals(Evaluation.REASON_ERROR, cyclic.evaluateVariable("a").getReason());
        assertEquals(1, originalReads.get());
    }

    @Test void nestedInvalidRequirementPathCannotBecomeNullGetter() throws Exception {
        Featurevisor sdk = instance(Map.of(), Map.of(
            "leaf", Map.of("type", "object", "defaultValue", Map.of()),
            "bad", variable(1, List.of(Map.of("variable", "leaf", "path", List.of("__proto__"), "operator", "notExists"))),
            "root", variable(1, List.of(Map.of("variable", "bad", "operator", "isNull")))
        ));
        assertEquals(Evaluation.REASON_ERROR, sdk.evaluateVariable("root").getReason());
    }

    @Test void cyclesPropagateThroughNegationButHandledGetterErrorsCanMatch() throws Exception {
        AtomicInteger brokenReads = new AtomicInteger();
        AtomicInteger failedReads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("errors").afterEvaluation((evaluation, options) -> {
            if (Evaluation.TYPE_FLAG.equals(evaluation.getType()) && "broken".equals(evaluation.getFeatureKey())) {
                brokenReads.incrementAndGet();
                evaluation.reason(Evaluation.REASON_ERROR).enabled(false);
            }
            if ("failed".equals(evaluation.getVariableKey())) {
                failedReads.incrementAndGet();
                evaluation.reason(Evaluation.REASON_ERROR).variableValue(null);
            }
            return evaluation;
        });
        Featurevisor sdk = instance(Map.of(
            "gate", Map.of("bucketBy", "userId", "conditions", List.of(Map.of("not", List.of(Map.of("variable", "source", "operator", "notNull")))), "traffic", List.of()),
            "broken", Map.of("bucketBy", "userId", "traffic", List.of()),
            "disabledGate", Map.of("bucketBy", "userId", "requiredFeatures", List.of(Map.of("feature", "broken", "enabled", false), Map.of("not", List.of("broken"))), "traffic", List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)))
        ), Map.of(
            "source", variable(1, List.of(Map.of("variable", "source", "operator", "equals", "value", 1))),
            "nullGate", variable(1, List.of(Map.of("variable", "failed", "operator", "isNull"), Map.of("variable", "failed", "operator", "isNull"))),
            "failed", variable(1, null)
        ), module);
        assertEquals(Evaluation.REASON_ERROR, sdk.evaluateVariable("source").getReason());
        assertTrue(sdk.isEnabled("disabledGate"));
        assertEquals(1, sdk.getVariable("nullGate"));
        assertEquals(1, brokenReads.get());
        assertEquals(1, failedReads.get());
    }

    @Test void reentrantAndConcurrentPublicCallsUseFreshSessions() throws Exception {
        Map<String, Object> variables = Map.of(
            "leaf", variable(1, null),
            "root", variable(1, List.of(requirement("leaf", 1), requirement("leaf", 1))));
        Featurevisor[] holder = new Featurevisor[1];
        AtomicBoolean nested = new AtomicBoolean();
        AtomicInteger leafReads = new AtomicInteger();
        List<DependencyOf> rootParents = java.util.Collections.synchronizedList(new ArrayList<>());
        FeaturevisorModule module = new FeaturevisorModule("reentrant").beforeEvaluation(options -> {
            if ("root".equals(options.getVariableKey())) rootParents.add(options.getDependencyOf());
            if ("leaf".equals(options.getVariableKey())) {
                leafReads.incrementAndGet();
                if (nested.compareAndSet(false, true)) {
                    try { holder[0].getVariable("root"); }
                    finally { nested.set(false); }
                }
            }
            return options;
        });
        holder[0] = instance(Map.of(), variables, module);
        assertEquals(1, holder[0].getVariable("root"));
        assertEquals(2, leafReads.get());
        assertEquals(2, rootParents.size());
        assertTrue(rootParents.stream().allMatch(value -> value == null));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) tasks.add(() -> holder[0].getVariable("root"));
            for (Future<Object> result : pool.invokeAll(tasks)) assertEquals(1, result.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void memoSnapshotDoesNotShareMutableValuesOrUnrelatedStates() {
        EvaluationSession session = new EvaluationSession();
        EvaluateOptions options = new EvaluateOptions(Evaluation.TYPE_VARIABLE, null, "leaf")
            .globalVariable(true).context(Map.of("nested", Map.of("key", 1)));
        assertTrue(session.beginRoot(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root")));
        AtomicInteger calls = new AtomicInteger();
        @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) session.read(options, () -> {
            calls.incrementAndGet();
            return new HashMap<>(Map.of("items", new ArrayList<>(List.of(1))));
        });
        @SuppressWarnings("unchecked") List<Object> firstItems = (List<Object>) first.get("items");
        firstItems.add(2);
        @SuppressWarnings("unchecked") Map<String, Object> second = (Map<String, Object>) session.read(options, () -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(List.of(1), second.get("items"));
        assertEquals(1, calls.get());
        options.setContext(Map.of("nested", Map.of("key", 2)));
        session.read(options, () -> { calls.incrementAndGet(); return null; });
        assertEquals(2, calls.get());
        session.endRoot();
    }

    @Test void snapshotDistinguishesDatesTextBooleansNumbersAndSkipsUnknownHostValues() {
        EvaluationSession session = new EvaluationSession();
        EvaluateOptions options = new EvaluateOptions(Evaluation.TYPE_VARIABLE, null, "leaf").globalVariable(true);
        assertTrue(session.beginRoot(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root")));
        AtomicInteger calls = new AtomicInteger();
        options.setContext(Map.of("at", new Date(0)));
        session.read(options, () -> calls.incrementAndGet());
        session.read(options, () -> calls.incrementAndGet());
        assertEquals(1, calls.get());
        options.setContext(Map.of("at", "1970-01-01T00:00:00Z"));
        session.read(options, () -> calls.incrementAndGet());
        options.setContext(Map.of("at", true));
        session.read(options, () -> calls.incrementAndGet());
        options.setContext(Map.of("at", 1));
        session.read(options, () -> calls.incrementAndGet());
        assertEquals(4, calls.get());
        options.setContext(Map.of("at", new Object()));
        session.read(options, () -> calls.incrementAndGet());
        session.read(options, () -> calls.incrementAndGet());
        assertEquals(6, calls.get());
        session.endRoot();
    }

    @Test void registrationRestorationDuringReadInvalidatesMemo() {
        EvaluationSession session = new EvaluationSession();
        ModulesManager manager = new ModulesManager(new ModulesManager.ModulesManagerOptions());
        EvaluateOptions options = new EvaluateOptions(Evaluation.TYPE_VARIABLE, null, "leaf")
            .globalVariable(true).context(Map.of()).modulesManager(manager);
        assertTrue(session.beginRoot(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root")));
        AtomicInteger reads = new AtomicInteger();
        session.read(options, () -> {
            reads.incrementAndGet();
            FeaturevisorModule temporary = new FeaturevisorModule("temporary");
            manager.add(temporary);
            manager.remove(temporary);
            return 1;
        });
        session.read(options, () -> { reads.incrementAndGet(); return 1; });
        assertEquals(2, reads.get());
        session.endRoot();
    }

    @Test void childStickyRestorationStartsFreshRequests() throws Exception {
        Map<String, Object> variables = Map.of(
            "leaf", variable(0, null),
            "root", variable(1, List.of(requirement("leaf", 1), requirement("leaf", 1))));
        Featurevisor sdk = instance(Map.of(), variables);
        sdk.setStickyVariables(Map.of("leaf", 0));
        ChildInstance child = sdk.spawn(Map.of(), new Featurevisor.SpawnOptions().stickyVariables(Map.of("leaf", 1)));
        assertNull(sdk.getVariable("root"));
        assertEquals(1, child.getVariable("root"));
        child.setStickyVariables(Map.of("leaf", 0), true);
        assertNull(child.getVariable("root"));
        child.setStickyVariables(Map.of("leaf", 1), true);
        assertEquals(1, child.getVariable("root"));
    }

    @Test void rootAndChildStickySetterRefreshPrerequisitesIncludingRestoration() throws Exception {
        for (boolean childMode : new boolean[] {false, true}) {
            Map<String, Object> variables = Map.of(
                "leaf", variable(0, null), "mutator", variable(1, null),
                "root", variable(1, List.of(requirement("leaf", 1), requirement("mutator", 1), requirement("leaf", 2))));
            Featurevisor[] sdkHolder = new Featurevisor[1];
            ChildInstance[] childHolder = new ChildInstance[1];
            AtomicInteger reads = new AtomicInteger();
            FeaturevisorModule module = new FeaturevisorModule("sticky-transition").afterEvaluation((evaluation, options) -> {
                if ("leaf".equals(evaluation.getVariableKey())) reads.incrementAndGet();
                if ("mutator".equals(evaluation.getVariableKey())) {
                    if (childMode) childHolder[0].setStickyVariables(Map.of("leaf", 2));
                    else sdkHolder[0].setStickyVariables(Map.of("leaf", 2));
                }
                return evaluation;
            });
            Featurevisor sdk = instance(Map.of(), variables, module);
            sdkHolder[0] = sdk;
            sdk.setStickyVariables(Map.of("leaf", 1));
            ChildInstance child = sdk.spawn(Map.of(), new Featurevisor.SpawnOptions().stickyVariables(Map.of("leaf", 1)));
            childHolder[0] = child;
            assertEquals(1, childMode ? child.getVariable("root") : sdk.getVariable("root"));
            assertEquals(2, reads.get());
        }

        Map<String, Object> variables = Map.of(
            "leaf", variable(0, null), "mutator", variable(1, null),
            "root", variable(1, List.of(requirement("leaf", 1), requirement("mutator", 1), requirement("leaf", 1))));
        Featurevisor[] sdkHolder = new Featurevisor[1];
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("restore").afterEvaluation((evaluation, options) -> {
            if ("leaf".equals(evaluation.getVariableKey())) reads.incrementAndGet();
            if ("mutator".equals(evaluation.getVariableKey())) {
                sdkHolder[0].setStickyVariables(Map.of("leaf", 2));
                sdkHolder[0].setStickyVariables(Map.of("leaf", 1));
            }
            return evaluation;
        });
        Featurevisor sdk = instance(Map.of(), variables, module);
        sdkHolder[0] = sdk;
        sdk.setStickyVariables(Map.of("leaf", 1));
        assertEquals(1, sdk.getVariable("root"));
        assertEquals(2, reads.get());
    }

    @Test void globalStickySetterInBeforeHookIsVisibleToGetter() throws Exception {
        Featurevisor[] sdkHolder = new Featurevisor[1];
        FeaturevisorModule module = new FeaturevisorModule("before-sticky").beforeEvaluation(options -> {
            if ("root".equals(options.getVariableKey())) sdkHolder[0].setStickyVariables(Map.of("root", 2));
            return options;
        });
        Featurevisor sdk = instance(Map.of(), Map.of("root", variable(1, null)), module);
        sdkHolder[0] = sdk;
        assertEquals(2, sdk.getVariable("root"));
    }

    @Test void stickySetterRefreshesEveryDependencyKindForRootAndChild() throws Exception {
        Map<String, Object> enabled = Map.of(
            "bucketBy", "userId",
            "traffic", List.of(Map.of("key", "all", "segments", "*", "percentage", 100000)));
        for (String kind : List.of("flag", "variation", "featureVariable", "globalVariable")) {
            for (boolean childMode : new boolean[] {false, true}) {
                Map<String, Object> features = new HashMap<>();
                Map<String, Object> variables = new HashMap<>();
                Object first;
                Object last;
                if ("flag".equals(kind) || "variation".equals(kind)) {
                    Map<String, Object> leaf = new HashMap<>(enabled);
                    leaf.put("variations", List.of(Map.of("value", "beta"), Map.of("value", "gamma")));
                    features.put("leaf", leaf);
                    features.put("mutator", enabled);
                    if ("flag".equals(kind)) {
                        first = Map.of("feature", "leaf", "enabled", true);
                        last = Map.of("feature", "leaf", "enabled", false);
                    } else {
                        first = Map.of("feature", "leaf", "variation", "beta");
                        last = Map.of("feature", "leaf", "variation", "gamma");
                    }
                    Map<String, Object> root = new HashMap<>(enabled);
                    root.put("requiredFeatures", List.of(first, "mutator", last));
                    features.put("root", root);
                } else {
                    variables.put("mutator", variable(1, null));
                    if ("featureVariable".equals(kind)) {
                        Map<String, Object> leaf = new HashMap<>(enabled);
                        leaf.put("variablesSchema", Map.of("count", Map.of("type", "integer", "defaultValue", 0)));
                        features.put("leaf", leaf);
                        first = Map.of("feature", "leaf", "variable", "count", "operator", "equals", "value", 1);
                        last = Map.of("feature", "leaf", "variable", "count", "operator", "equals", "value", 2);
                    } else {
                        variables.put("leaf", variable(0, null));
                        first = requirement("leaf", 1);
                        last = requirement("leaf", 2);
                    }
                    variables.put("root", variable(1, List.of(first, requirement("mutator", 1), last)));
                }
                Map<String, Object> initialFeature = Map.of("enabled", true, "variation", "beta", "variables", Map.of("count", 1));
                Map<String, Object> changedFeature = Map.of("enabled", !"flag".equals(kind), "variation", "gamma", "variables", Map.of("count", 2));
                Featurevisor[] sdkHolder = new Featurevisor[1];
                ChildInstance[] childHolder = new ChildInstance[1];
                AtomicInteger reads = new AtomicInteger();
                FeaturevisorModule module = new FeaturevisorModule("kind-transition").afterEvaluation((evaluation, options) -> {
                    if ("leaf".equals(evaluation.getFeatureKey()) ||
                        ("globalVariable".equals(kind) && "leaf".equals(evaluation.getVariableKey()))) {
                        if (("flag".equals(kind) && Evaluation.TYPE_FLAG.equals(evaluation.getType()))
                            || ("variation".equals(kind) && Evaluation.TYPE_VARIATION.equals(evaluation.getType()))
                            || (("featureVariable".equals(kind) || "globalVariable".equals(kind))
                                && Evaluation.TYPE_VARIABLE.equals(evaluation.getType()))) reads.incrementAndGet();
                    }
                    if ("mutator".equals(evaluation.getFeatureKey()) || "mutator".equals(evaluation.getVariableKey())) {
                        if (childMode) {
                            childHolder[0].setStickyFeatures(Map.of("leaf", changedFeature));
                            childHolder[0].setStickyVariables(Map.of("leaf", 2));
                        } else {
                            sdkHolder[0].setStickyFeatures(Map.of("leaf", changedFeature));
                            sdkHolder[0].setStickyVariables(Map.of("leaf", 2));
                        }
                    }
                    return evaluation;
                });
                Featurevisor sdk = instance(features, variables, module);
                sdkHolder[0] = sdk;
                sdk.setStickyFeatures(Map.of("leaf", initialFeature));
                sdk.setStickyVariables(Map.of("leaf", 1));
                ChildInstance child = sdk.spawn(Map.of(), new Featurevisor.SpawnOptions()
                    .stickyFeatures(Map.of("leaf", initialFeature)).stickyVariables(Map.of("leaf", 1)));
                childHolder[0] = child;
                if ("flag".equals(kind) || "variation".equals(kind)) {
                    assertTrue(childMode ? child.isEnabled("root") : sdk.isEnabled("root"), kind + ", child=" + childMode);
                } else {
                    assertEquals(1, childMode ? child.getVariable("root") : sdk.getVariable("root"), kind + ", child=" + childMode);
                }
                assertEquals(2, reads.get(), kind + ", child=" + childMode);
            }
        }
    }

    @Test void redirectedDependencyMayEqualAncestorWithoutAnotherRead() throws Exception {
        FeaturevisorModule module = new FeaturevisorModule("redirect-to-sticky").beforeEvaluation(options -> {
            if (!"leaf".equals(options.getVariableKey())) return options;
            EvaluateOptions redirected = options.copy();
            redirected.setVariableKey("root");
            redirected.setStickyVariables(Map.of("root", 1));
            return redirected;
        });
        Featurevisor sdk = instance(Map.of(), Map.of(
            "root", variable(1, List.of(requirement("leaf", 1))), "leaf", variable(1, null)), module);
        assertEquals(1, sdk.getVariable("root"));
    }

    @Test void retainedModuleOptionsDoNotKeepStickySourceOrActiveRequest() throws Exception {
        EvaluateOptions[] retained = new EvaluateOptions[1];
        FeaturevisorModule module = new FeaturevisorModule("retain").beforeEvaluation(options -> {
            if ("leaf".equals(options.getVariableKey())) retained[0] = options;
            return options;
        });
        Featurevisor sdk = instance(Map.of(), Map.of(
            "root", variable(1, List.of(requirement("leaf", 1))), "leaf", variable(1, null)), module);
        assertEquals(1, sdk.getVariable("root"));
        assertEquals(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "root"), retained[0].getDependencyOf());
        assertNull(retained[0].getStickySource());
        assertNull(retained[0].getSession().current());
    }

    private void assertPublicReadCount(Object contextValue, Object resultValue, int expectedReads) throws Exception {
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("host-value").afterEvaluation((evaluation, options) -> {
            if ("leaf".equals(evaluation.getVariableKey())) {
                reads.incrementAndGet();
                evaluation.setVariableValue(resultValue);
            }
            return evaluation;
        });
        Map<String, Object> required = Map.of("variable", "leaf", "operator", "notNull");
        Featurevisor sdk = instance(Map.of(), Map.of(
            "root", variable(1, List.of(required, required)), "leaf", variable(1, null)), module);
        assertEquals(1, sdk.getVariable("root", Map.of("host", contextValue)));
        assertEquals(expectedReads, reads.get());
        assertSame(resultValue, sdk.getVariable("leaf", Map.of("host", contextValue)));
    }

    private List<Object> unsupportedGraphs() {
        Map<String, Object> mapCycle = new HashMap<>();
        mapCycle.put("nested", List.of(mapCycle));
        List<Object> listCycle = new ArrayList<>();
        listCycle.add(Map.of("nested", listCycle));
        Object[] arrayCycle = new Object[1];
        arrayCycle[0] = Map.of("nested", arrayCycle);

        Object deep = 1;
        for (int level = 0; level < 1000; level++) deep = List.of(Map.of("nested", deep));
        Object shared = 1;
        for (int level = 0; level < 20; level++) shared = List.of(shared, shared);
        return List.of(mapCycle, listCycle, arrayCycle, deep, shared);
    }

    @Test void publicGettersBypassMemoForCyclicAndOversizedContexts() throws Exception {
        for (Object context : unsupportedGraphs()) assertPublicReadCount(context, 1, 2);
    }

    @Test void publicGettersBypassMemoForCyclicAndOversizedResults() throws Exception {
        for (Object result : unsupportedGraphs()) assertPublicReadCount(1, result, 2);
    }

    private void assertPublicContextTransition(Object first, Object second, Function<Object, Integer> observe) throws Exception {
        AtomicInteger reads = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("context-transition").afterEvaluation((evaluation, options) -> {
            if ("leaf".equals(evaluation.getVariableKey())) {
                reads.incrementAndGet();
                evaluation.setVariableValue(observe.apply(options.getContext().get("host")));
            }
            if ("mutator".equals(evaluation.getVariableKey())) options.getContext().put("host", second);
            return evaluation;
        });
        Featurevisor sdk = instance(Map.of(), Map.of(
            "leaf", variable(0, null), "mutator", variable(1, null),
            "root", variable(1, List.of(requirement("leaf", 1), requirement("mutator", 1), requirement("leaf", 2)))), module);
        assertEquals(1, sdk.getVariable("root", Map.of("host", first)));
        assertEquals(2, reads.get());
    }

    @Test void publicMemoDistinguishesNumericAndCollectionRepresentations() throws Exception {
        assertPublicContextTransition(1, 1.0, value -> value instanceof Integer ? 1 : 2);
        assertPublicContextTransition(0.0, -0.0, value -> Double.doubleToRawLongBits((Double) value) == 0 ? 1 : 2);
        assertPublicContextTransition(0.0f, -0.0f, value -> Float.floatToRawIntBits((Float) value) == 0 ? 1 : 2);
        assertPublicContextTransition(new BigDecimal("1"), new BigDecimal("1.0"), value -> ((BigDecimal) value).scale() == 0 ? 1 : 2);
        assertPublicContextTransition(List.of(1), new Integer[] {1}, value -> value instanceof List ? 1 : 2);
        assertPublicContextTransition(new Integer[] {1}, new int[] {1}, value -> value instanceof Integer[] ? 1 : 2);
    }

    private static final class MutableNumber extends Number {
        private int value = 1;
        @Override public int intValue() { return value; }
        @Override public long longValue() { return value; }
        @Override public float floatValue() { return value; }
        @Override public double doubleValue() { return value; }
        @Override public String toString() { return Integer.toString(value); }
    }

    @Test void publicMemoBypassesMutableHostNumbersInInputsAndResults() throws Exception {
        for (Number value : List.of(new AtomicInteger(1), new MutableNumber())) {
            assertPublicReadCount(Map.of("nested", List.of(value)), 1, 2);
            assertPublicReadCount(1, value, 2);
            assertPublicReadCount(1, Map.of("nested", List.of(value)), 2);
        }
    }

    @Test void unsupportedContextDoesNotInspectResultForMemoisation() throws Exception {
        List<Object> opaqueResult = new AbstractList<Object>() {
            @Override public Object get(int index) { throw new AssertionError("Result must not be inspected"); }
            @Override public int size() { throw new AssertionError("Result must not be inspected"); }
        };
        assertPublicReadCount(new Object(), opaqueResult, 2);
    }

    @Test void immutableNumbersAndSharedAcyclicInputsStillMemoise() throws Exception {
        for (Number value : List.<Number>of((byte) 1, (short) 1, 1, 1L, 1.0f, 1.0,
                new BigInteger("12345678901234567890"), new BigDecimal("1.00"))) {
            assertPublicReadCount(value, value, 1);
        }
        Map<String, Object> shared = Map.of("value", 1);
        assertPublicReadCount(List.of(shared, shared), 1, 1);
    }
}
