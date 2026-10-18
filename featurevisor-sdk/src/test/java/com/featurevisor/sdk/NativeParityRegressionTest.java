package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeParityRegressionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DatafileContent datafile(Map<String, Object> features, Map<String, Object> variables) throws Exception {
        return DatafileContent.fromJson(JSON.writeValueAsString(Map.of(
            "schemaVersion", "4", "revision", "parity", "segments", Map.of(),
            "features", features, "variables", variables)));
    }

    @Test void internalFlagChecksKeepTheOuterRequesterAndMemoAcrossSelectorSites() throws Exception {
        Object dependency = Map.of("feature", "gate");
        Map<String, Object> all = Map.of("key", "all", "conditions", "*", "percentage", 100000);
        DatafileContent data = datafile(Map.of(
            "gate", Map.of("bucketBy", "userId", "traffic", List.of(all)),
            "subject", Map.of("bucketBy", "userId", "conditions", List.of(dependency, dependency),
                "traffic", List.of(Map.of("key", "all", "conditions", dependency, "percentage", 100000,
                    "allocation", List.of(Map.of("variation", "on", "range", List.of(0, 100000))))),
                "variations", List.of(Map.of("value", "on")),
                "variablesSchema", Map.of("value", Map.of("type", "string", "defaultValue", "ok")))), Map.of());
        for (boolean transform : List.of(false, true)) {
            List<DependencyOf> parents = new ArrayList<>();
            FeaturevisorModule module = new FeaturevisorModule("requester").beforeEvaluation(options -> {
                if (options.getDependencyOf() != null) {
                    parents.add(options.getDependencyOf());
                    assertEquals("condition", options.getDependencyPurpose());
                } else if (transform) {
                    return options.copy().featureKey("subject");
                }
                return options;
            });
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(data).modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
            try {
                String key = transform ? "alias" : "subject";
                for (int attempt = 0; attempt < 2; attempt++) {
                    parents.clear();
                    assertEquals("on", f.getVariation(key));
                    assertEquals(List.of(new DependencyOf(Evaluation.TYPE_VARIATION, "subject", null)), parents);
                    parents.clear();
                    assertEquals("ok", f.getVariable(key, "value"));
                    assertEquals(List.of(new DependencyOf(Evaluation.TYPE_VARIABLE, "subject", "value")), parents);
                }
            } finally { f.close(); }
        }
    }

    @Test void repeatedInvalidJsonLeavesMemoizeTheOrdinaryGetterNull() throws Exception {
        for (boolean featureSource : List.of(false, true)) {
            Object leaf = featureSource
                ? Map.of("feature", "sourceFeature", "variable", "source", "operator", "isNull")
                : Map.of("variable", "source", "operator", "isNull");
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            AtomicInteger reads = new AtomicInteger();
            FeaturevisorModule module = new FeaturevisorModule("reads").beforeEvaluation(options -> {
                if ("source".equals(options.getVariableKey())) {
                    reads.incrementAndGet();
                    assertEquals(new DependencyOf(Evaluation.TYPE_VARIABLE, null, "subject"), options.getDependencyOf());
                }
                return options;
            });
            DatafileContent data = datafile(featureSource ? Map.of("sourceFeature", Map.of(
                "bucketBy", "userId", "traffic", List.of(Map.of("key", "all", "conditions", "*", "percentage", 100000)),
                "variablesSchema", Map.of("source", Map.of("type", "json", "defaultValue", "{oops")))) : Map.of(),
                Map.of("source", Map.of("type", "json", "defaultValue", "{oops"),
                    "subject", Map.of("type", "string", "defaultValue", "ok", "conditions", List.of(leaf, leaf))));
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(data).modules(List.of(module)).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add));
            try {
                for (int attempt = 1; attempt <= 2; attempt++) {
                    assertEquals("ok", f.getVariable("subject"));
                    assertEquals(attempt, reads.get());
                    assertEquals(attempt, diagnostics.stream().filter(d -> d.getMessage().equals("getVariable failed")).count());
                }
            } finally { f.close(); }
        }
    }

    @Test void jsonStringAliasesDecodeOnceAndKeepConditionAndCompositionMemosSeparate() throws Exception {
        for (String value : List.of("123", "null", "{\"nested\":true}", "\"quoted\"")) {
            Object leaf = Map.of("variable", "source", "operator", "equals", "value", value);
            AtomicInteger reads = new AtomicInteger();
            List<String> purposes = new ArrayList<>();
            FeaturevisorModule module = new FeaturevisorModule("purposes").beforeEvaluation(options -> {
                if ("source".equals(options.getVariableKey())) {
                    reads.incrementAndGet(); purposes.add(options.getDependencyPurpose());
                }
                return options;
            });
            Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(datafile(Map.of(), Map.of(
                    "source", Map.of("type", "json", "defaultValue", JSON.writeValueAsString(value)),
                    "alias", Map.of("type", "json", "compose", "source"),
                    "aliasAgain", Map.of("type", "json", "compose", "alias"),
                    "subject", Map.of("type", "object", "conditions", List.of(leaf, leaf), "compose", List.of(
                        Map.of("key", "first", "variable", "source"), Map.of("key", "second", "variable", "source"))))))
                .modules(List.of(module)).logLevel(FeaturevisorLogLevel.FATAL));
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    reads.set(0); purposes.clear();
                    assertEquals(Map.of("first", value, "second", value), f.getVariable("subject"));
                    assertEquals(2, reads.get());
                    assertEquals(List.of("condition", "composition"), purposes);
                    assertEquals(value, f.getVariable("aliasAgain"));
                }
            } finally { f.close(); }
        }
    }
}
