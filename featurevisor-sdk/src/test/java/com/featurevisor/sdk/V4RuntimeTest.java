package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class V4RuntimeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Featurevisor instance(String json, FeaturevisorModule... modules) throws Exception {
        return Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(mapper.readValue(json, DatafileContent.class))
            .modules(List.of(modules))
            .logLevel(FeaturevisorLogLevel.FATAL));
    }

    @Test void nullOperatorsDistinguishMissingFromNull() {
        assertTrue(Conditions.allConditionsAreMatched(
            Map.of("attribute", "value", "operator", "isNull"), nullValue()));
        assertFalse(Conditions.allConditionsAreMatched(
            Map.of("attribute", "missing", "operator", "isNull"), Map.of()));
        assertFalse(Conditions.allConditionsAreMatched(
            Map.of("attribute", "value", "operator", "notNull"), nullValue()));
        assertFalse(Conditions.allConditionsAreMatched(
            Map.of("attribute", "missing", "operator", "notNull"), Map.of()));
    }

    @Test void emptyRequiredVariablesMatchOnlySupportedContainerContents() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"empty-requirements","segments":{},"features":{},"variables":{
              "emptyString":{"type":"string","defaultValue":""},
              "whitespace":{"type":"string","defaultValue":" "},
              "emptyArray":{"type":"array","defaultValue":[]},
              "arrayWithNull":{"type":"array","defaultValue":[null]},
              "emptyObject":{"type":"object","defaultValue":{}},
              "objectWithNull":{"type":"object","defaultValue":{"value":null}},
              "nullValue":{"type":"object","defaultValue":null},
              "number":{"type":"integer","defaultValue":0},
              "boolean":{"type":"boolean","defaultValue":false},
              "nested":{"type":"object","defaultValue":{"items":[]}},
              "emptyStringGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"emptyString","operator":"empty"}]},
              "whitespaceGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"whitespace","operator":"notEmpty"}]},
              "emptyArrayGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"emptyArray","operator":"empty"}]},
              "arrayWithNullGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"arrayWithNull","operator":"notEmpty"}]},
              "emptyObjectGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"emptyObject","operator":"empty"}]},
              "objectWithNullGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"objectWithNull","operator":"notEmpty"}]},
              "nestedGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"nested","path":["items"],"operator":"empty"}]},
              "nullEmptyGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"nullValue","operator":"empty"}]},
              "nullNotEmptyGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"nullValue","operator":"notEmpty"}]},
              "missingGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"missing","operator":"empty"}]},
              "numberGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"number","operator":"notEmpty"}]},
              "booleanGate":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[{"variable":"boolean","operator":"empty"}]}
            }}
            """);

        for (String key : List.of("emptyStringGate", "whitespaceGate", "emptyArrayGate", "arrayWithNullGate",
                "emptyObjectGate", "objectWithNullGate", "nestedGate")) {
            assertEquals("matched", f.getVariable(key), key);
        }
        for (String key : List.of("nullEmptyGate", "nullNotEmptyGate", "missingGate", "numberGate", "booleanGate")) {
            assertEquals("miss", f.getVariable(key), key);
            assertEquals(Evaluation.REASON_CONDITIONS_UNMET, f.evaluateVariable(key).getReason(), key);
        }
    }

    private Map<String, Object> nullValue() {
        java.util.HashMap<String, Object> value = new java.util.HashMap<>();
        value.put("value", null);
        return value;
    }

    @Test void presentEmptyProgramSuppressesLegacyOverridesAndEmptyContainersRemainValues() throws Exception {
        DatafileContent datafile = mapper.readValue("""
            {"schemaVersion":"2","revision":"empty-program","segments":{},"features":{},"variables":{
              "source":{"type":"boolean","defaultValue":true},
              "subject":{"type":"object","defaultValue":{"default":true},
                "overrides":[{"segments":"*","value":{"wrong":true},"conditions":[
                  {"variable":"source","operator":"equals","value":true}
                ]}],"overrideProgram":[]},
              "emptyArray":{"type":"array","defaultValue":[]},
              "emptyObject":{"type":"object","defaultValue":{}}
            }}
            """, DatafileContent.class);
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile)
            .logLevel(FeaturevisorLogLevel.FATAL));

        assertEquals(Map.of("default", true), f.getVariable("subject"));
        Evaluation evaluation = f.evaluateVariable("subject");
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, evaluation.getReason());
        assertNull(evaluation.getVariableOverrideIndex());
        assertNull(evaluation.getVariableOverrideKey());
        assertNull(evaluation.getVariableOverridePath());
        assertNull(evaluation.getVariableOverridePaths());
        assertEquals(List.of(), f.getVariableArray("emptyArray", Map.of(), null));
        assertEquals(Map.of(), f.getVariableObject("emptyObject", Map.of(), null));

        Dependencies.Index dependencies = Dependencies.create(datafile);
        assertFalse(dependencies.dependents[1].getOrDefault("source", java.util.Set.of()).contains("1:subject"));
    }

    @Test void legacyRequiredFieldAndLegacyKeyShapeAreIgnored() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"legacy","segments":{},"features":{
              "off":{"bucketBy":"userId","traffic":[]},
              "subject":{"bucketBy":"userId","required":[{"key":"off"}],
                "traffic":[{"key":"all","segments":"*","percentage":100000}]}
            }}
            """);
        assertTrue(f.isEnabled("subject"));
    }

    @Test void accumulationRunsOneModuleEnvelopeAndPublishesCompleteMetadata() throws Exception {
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("observer")
            .beforeEvaluation(options -> { before.incrementAndGet(); return options; })
            .afterEvaluation((evaluation, options) -> { after.incrementAndGet(); return evaluation; });
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"modules","segments":{},"features":{},"variables":{
              "subject":{"type":"object","defaultValue":{},"overrideProgram":[
                {"key":"one","keyPath":["one"],"segments":"*","operations":[{"op":"set","path":["one"],"value":1}],"onMatch":1,"onMiss":1},
                {"key":"two","keyPath":["two"],"segments":"*","operations":[{"op":"set","path":["two"],"value":2}],"onMatch":2,"onMiss":2}
              ]}
            }}
            """, module);
        Evaluation evaluation = f.evaluateVariable("subject");
        assertEquals(Map.of("one", 1, "two", 2), evaluation.getVariableValue());
        assertEquals(List.of(List.of("one"), List.of("two")), evaluation.getVariableOverridePaths());
        assertEquals("two", evaluation.getVariableOverrideKey());
        assertEquals(1, evaluation.getVariableOverrideIndex());
        assertEquals(1, before.get());
        assertEquals(1, after.get());
    }

    @Test void childStickyValuesDriveRequirementsWithoutLeakingParentDefaults() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"child","segments":{},"features":{},"variables":{
              "source":{"type":"object","defaultValue":{"enabled":false}},
              "subject":{"type":"string","defaultValue":"yes","disabledValue":"no","conditions":[
                {"variable":"source","path":["enabled"],"operator":"equals","value":true}
              ]}
            }}
            """);
        ChildInstance child = f.spawn(Map.of(), new Featurevisor.SpawnOptions()
            .stickyVariables(Map.of("source", Map.of("enabled", true))));
        assertEquals("no", f.getVariable("subject"));
        assertEquals("yes", child.getVariable("subject"));
        assertEquals("yes", child.getVariable("subject", Map.of(),
            new Featurevisor.OverrideOptions().defaultVariableValue("caller")));
    }

    @Test void defaultChildStickyMapsAreIsolatedFromParent() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"child-defaults","segments":{},"features":{
              "flag":{"bucketBy":"userId","force":[{"conditions":"*","enabled":false}]}
            },"variables":{
              "source":{"type":"boolean","defaultValue":false},
              "subject":{"type":"string","defaultValue":"yes","disabledValue":"no","conditions":[
                {"variable":"source","operator":"equals","value":true}
              ]}
            }}
            """);
        f.setStickyFeatures(Map.of("flag", Map.of("enabled", true)));
        f.setStickyVariables(Map.of("source", true));
        ChildInstance child = f.spawn();

        assertTrue(f.isEnabled("flag"));
        assertEquals("yes", f.getVariable("subject"));
        assertFalse(child.isEnabled("flag"));
        assertEquals("no", child.getVariable("subject"));
    }

    @Test void mixedDependencyGraphPropagatesAcrossEveryNamespace() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"one","segments":{},"features":{
              "fromVariable":{"hash":"fv","bucketBy":"userId","conditions":[{"variable":"source","operator":"equals","value":true}],"traffic":[]},
              "fromVariableAgain":{"hash":"fva","bucketBy":"userId","conditions":[{"variable":"middle","operator":"equals","value":true}],"traffic":[]}
            },"variables":{
              "source":{"hash":"source-1","type":"boolean","defaultValue":true},
              "middle":{"hash":"middle","type":"boolean","defaultValue":true,"requiredFeatures":["fromVariable"]},
              "program":{"hash":"program","type":"boolean","defaultValue":true,"overrideProgram":[
                {"key":"branch","keyPath":["branch"],"conditions":[{"or":[{"variable":"middle","operator":"equals","value":true},{"variable":"unused","operator":"equals","value":true}]}],"operations":[{"op":"set","path":[],"value":true}],"onMatch":1,"onMiss":1}
              ]}
            }}
            """);
        FeaturevisorEventDetails[] event = new FeaturevisorEventDetails[1];
        f.on(FeaturevisorEventName.DATAFILE_SET, details -> event[0] = details);
        f.setDatafile(mapper.readValue("""
            {"schemaVersion":"2","revision":"two","segments":{},"features":{},"variables":{
              "source":{"hash":"source-2","type":"boolean","defaultValue":false}
            }}
            """, DatafileContent.class));
        assertNotNull(event[0]);
        assertTrue(((List<?>) event[0].get("features")).containsAll(List.of("fromVariable", "fromVariableAgain")));
        assertTrue(((List<?>) event[0].get("variables")).containsAll(List.of("source", "middle", "program")));
    }

    @Test void hashlessAndEmptyHashEntitiesAlwaysNotifyWithMixedDependencies() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"hash-one","segments":{},"features":{
              "featureHashlessSame":{"bucketBy":"userId","traffic":[]},
              "featureHashlessChanged":{"bucketBy":"userId","force":[{"conditions":"*","enabled":true}]},
              "featureEmptySame":{"hash":"","bucketBy":"userId","traffic":[]},
              "featureStable":{"hash":"stable","bucketBy":"userId","traffic":[]},
              "dependentFeature":{"hash":"dependent","bucketBy":"userId","requiredFeatures":["featureHashlessSame"],"traffic":[]},
              "featureFromVariable":{"hash":"feature-variable","bucketBy":"userId","conditions":[{"variable":"variableHashlessChanged","operator":"equals","value":true}],"traffic":[]}
            },"variables":{
              "variableHashlessSame":{"type":"boolean","defaultValue":true},
              "variableHashlessChanged":{"type":"boolean","defaultValue":true},
              "variableEmptySame":{"hash":"","type":"boolean","defaultValue":true},
              "variableStable":{"hash":"stable","type":"boolean","defaultValue":true},
              "variableFromFeature":{"hash":"variable-feature","type":"boolean","defaultValue":true,"requiredFeatures":["featureHashlessSame"]}
            }}
            """);
        FeaturevisorEventDetails[] event = new FeaturevisorEventDetails[1];
        f.on(FeaturevisorEventName.DATAFILE_SET, details -> event[0] = details);
        f.setDatafile(mapper.readValue("""
            {"schemaVersion":"2","revision":"hash-two","segments":{},"features":{
              "featureHashlessSame":{"bucketBy":"userId","traffic":[]},
              "featureHashlessChanged":{"bucketBy":"userId","force":[{"conditions":"*","enabled":false}]},
              "featureEmptySame":{"hash":"","bucketBy":"userId","traffic":[]},
              "featureStable":{"hash":"stable","bucketBy":"userId","traffic":[]},
              "dependentFeature":{"hash":"dependent","bucketBy":"userId","requiredFeatures":["featureHashlessSame"],"traffic":[]},
              "featureFromVariable":{"hash":"feature-variable","bucketBy":"userId","conditions":[{"variable":"variableHashlessChanged","operator":"equals","value":true}],"traffic":[]}
            },"variables":{
              "variableHashlessSame":{"type":"boolean","defaultValue":true},
              "variableHashlessChanged":{"type":"boolean","defaultValue":false},
              "variableEmptySame":{"hash":"","type":"boolean","defaultValue":true},
              "variableStable":{"hash":"stable","type":"boolean","defaultValue":true},
              "variableFromFeature":{"hash":"variable-feature","type":"boolean","defaultValue":true,"requiredFeatures":["featureHashlessSame"]}
            }}
            """, DatafileContent.class), true);

        List<?> features = (List<?>) event[0].get("features");
        List<?> variables = (List<?>) event[0].get("variables");
        assertTrue(features.containsAll(List.of("featureHashlessSame", "featureHashlessChanged", "featureEmptySame", "dependentFeature", "featureFromVariable")));
        assertFalse(features.contains("featureStable"));
        assertTrue(variables.containsAll(List.of("variableHashlessSame", "variableHashlessChanged", "variableEmptySame", "variableFromFeature")));
        assertFalse(variables.contains("variableStable"));
    }

    @Test void reentrantCallbacksAndConcurrentCyclesRemainIsolated() throws Exception {
        AtomicBoolean reenter = new AtomicBoolean(true);
        Featurevisor[] holder = new Featurevisor[1];
        FeaturevisorModule module = new FeaturevisorModule("reentrant")
            .beforeEvaluation(options -> {
                if ("ok".equals(options.getVariableKey()) && reenter.getAndSet(false)) {
                    assertEquals(Boolean.TRUE, holder[0].getVariable("helper"));
                }
                return options;
            });
        holder[0] = instance("""
            {"schemaVersion":"2","revision":"concurrent","segments":{},"features":{},"variables":{
              "helper":{"type":"boolean","defaultValue":true},
              "ok":{"type":"boolean","defaultValue":true},
              "cycle":{"type":"boolean","defaultValue":true,"conditions":[{"variable":"cycle","operator":"isNull"}]}
            }}
            """, module);
        assertEquals(Boolean.TRUE, holder[0].getVariable("ok"));

        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> calls = List.of(
                () -> holder[0].evaluateVariable("cycle").getReason(),
                () -> String.valueOf(holder[0].getVariable("ok")));
            List<Future<String>> results = pool.invokeAll(calls);
            assertEquals("error", results.get(0).get());
            assertEquals("true", results.get(1).get());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(Boolean.TRUE, holder[0].getVariable("ok"));
    }

    @Test void sourceGetterErrorsBecomeNullButCyclesStillPropagate() throws Exception {
        FeaturevisorModule module = new FeaturevisorModule("throw-source")
            .beforeEvaluation(options -> {
                if ("source".equals(options.getVariableKey())) throw new IllegalStateException("source failed");
                return options;
            });
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"getter-error","segments":{},"features":{},"variables":{
              "source":{"type":"boolean","defaultValue":true},
              "subject":{"type":"string","defaultValue":"matched","conditions":[
                {"variable":"source","operator":"isNull"}
              ]},
              "emptySubject":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[
                {"variable":"source","operator":"empty"}
              ]},
              "notEmptySubject":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[
                {"variable":"source","operator":"notEmpty"}
              ]},
              "cycle":{"type":"boolean","defaultValue":true,"conditions":[
                {"variable":"cycle","operator":"isNull"}
              ]}
            }}
            """, module);

        Evaluation subject = f.evaluateVariable("subject");
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, subject.getReason());
        assertEquals("matched", subject.getVariableValue());
        assertEquals("miss", f.getVariable("emptySubject"));
        assertEquals("miss", f.getVariable("notEmptySubject"));
        assertEquals(Evaluation.REASON_ERROR, f.evaluateVariable("cycle").getReason());
    }

    @Test void malformedJsonGettersReportOnceAndRemainNullForRequirements() throws Exception {
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        AtomicInteger globalCalls = new AtomicInteger();
        AtomicInteger featureCalls = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("json-count")
            .beforeEvaluation(options -> {
                if ("globalBad".equals(options.getVariableKey())) globalCalls.incrementAndGet();
                if ("featureBad".equals(options.getVariableKey())) featureCalls.incrementAndGet();
                return options;
            });
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(mapper.readValue("""
                {"schemaVersion":"2","revision":"json-errors","segments":{},"features":{
                  "sourceFeature":{"bucketBy":"userId","traffic":[{"key":"all","segments":"*","percentage":100000,"variables":{"featureBad":"{"}}],"variablesSchema":{
                    "featureBad":{"type":"json","defaultValue":"{"}
                  }}
                },"variables":{
                  "globalBad":{"type":"json","defaultValue":"{"},
                  "nestedGlobal":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[
                    {"variable":"globalBad","operator":"isNull"}
                  ]},
                  "nestedFeature":{"type":"string","defaultValue":"matched","disabledValue":"miss","conditions":[
                    {"feature":"sourceFeature","variable":"featureBad","operator":"isNull"}
                  ]}
                }}
                """, DatafileContent.class))
            .context(Map.of("userId", "one"))
            .modules(List.of(module))
            .logLevel(FeaturevisorLogLevel.DEBUG)
            .onDiagnostic(diagnostics::add));
        ChildInstance child = f.spawn();

        assertNull(f.getVariable("globalBad"));
        assertNull(f.getVariable("sourceFeature", "featureBad"));
        assertNull(child.getVariable("globalBad"));
        assertNull(child.getVariable("sourceFeature", "featureBad"));
        assertEquals("matched", f.getVariable("nestedGlobal"));
        assertEquals("matched", f.getVariable("nestedFeature"));

        List<FeaturevisorDiagnostic> errors = diagnostics.stream()
            .filter(diagnostic -> "evaluation_error".equals(diagnostic.getCode()))
            .collect(Collectors.toList());
        String errorDetails = errors.stream()
            .map(diagnostic -> diagnostic.getDetails().toString())
            .collect(Collectors.joining(", "));
        assertEquals(3, globalCalls.get());
        assertEquals(3, featureCalls.get());
        for (FeaturevisorDiagnostic diagnostic : errors) {
            assertEquals("getVariable failed", diagnostic.getMessage());
            assertNotNull(diagnostic.getOriginalError());
        }
        assertEquals(3, errors.stream()
            .filter(diagnostic -> "globalBad".equals(diagnostic.getDetails().get("variableKey"))
                && !diagnostic.getDetails().containsKey("featureKey"))
            .count(), errorDetails);
        assertEquals(3, errors.stream()
            .filter(diagnostic -> "featureBad".equals(diagnostic.getDetails().get("variableKey"))
                && "sourceFeature".equals(diagnostic.getDetails().get("featureKey")))
            .count(), errorDetails);
        assertEquals(6, errors.size());
    }

    @Test void malformedStringifiedSegmentsAbortGlobalOverridesAndPrograms() throws Exception {
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(mapper.readValue("""
                {"schemaVersion":"2","revision":"segment-errors","segments":{},"features":{},"variables":{
                  "ordinary":{"type":"string","defaultValue":"default","overrides":[
                    {"segments":"{","value":"wrong"}
                  ]},
                  "program":{"type":"string","defaultValue":"default","overrideProgram":[
                    {"key":"bad","keyPath":["bad"],"segments":"{","operations":[
                      {"op":"set","path":[],"value":"wrong"}
                    ],"onMatch":1,"onMiss":1}
                  ]}
                }}
                """, DatafileContent.class))
            .logLevel(FeaturevisorLogLevel.DEBUG)
            .onDiagnostic(diagnostics::add));

        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, f.evaluateVariable("ordinary").getReason());
        assertEquals(Evaluation.REASON_VARIABLE_DEFAULT, f.evaluateVariable("program").getReason());
        assertEquals("default", f.getVariable("ordinary"));
        assertEquals("default", f.getVariable("program"));
        List<FeaturevisorDiagnostic> errors = diagnostics.stream()
            .filter(diagnostic -> "conditions_parse_error".equals(diagnostic.getCode()))
            .collect(Collectors.toList());
        assertEquals(2, errors.size());
        assertEquals(java.util.Set.of("ordinary", "program"), errors.stream()
            .map(diagnostic -> (String) diagnostic.getDetails().get("entityKey"))
            .collect(Collectors.toSet()));
        for (FeaturevisorDiagnostic diagnostic : errors) {
            assertEquals(java.util.Set.of("conditions", "entityType", "entityKey"), diagnostic.getDetails().keySet());
            assertEquals("variable", diagnostic.getDetails().get("entityType"));
        }
    }

    @Test void requiredFeatureSourceErrorsUseOrdinaryDisabledSemantics() throws Exception {
        FeaturevisorModule module = new FeaturevisorModule("throw-feature-source")
            .beforeEvaluation(options -> {
                if ("source".equals(options.getFeatureKey())) throw new IllegalStateException("source failed");
                return options;
            });
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"feature-getter-error","segments":{},"features":{
              "source":{"bucketBy":"userId","force":[{"conditions":"*","enabled":true}]}
            },"variables":{
              "disabledMatch":{"type":"string","defaultValue":"matched","disabledValue":"miss","requiredFeatures":[
                {"feature":"source","enabled":false}
              ]},
              "notMatch":{"type":"string","defaultValue":"matched","disabledValue":"miss","requiredFeatures":[
                {"not":["source"]}
              ]}
            }}
            """, module);

        assertEquals("matched", f.getVariable("disabledMatch"));
        assertEquals("matched", f.getVariable("notMatch"));
    }

    @Test void requirementPathsFollowJavaScriptArrayOwnProperties() throws Exception {
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"array-paths","segments":{},"features":{},"variables":{
              "array":{"type":"array","defaultValue":["first"]},
              "object":{"type":"object","defaultValue":{"0":"first"}},
              "stringIndex":{"type":"boolean","defaultValue":true,"conditions":[
                {"variable":"array","path":["0"],"operator":"equals","value":"first"}
              ]},
              "numericIndex":{"type":"boolean","defaultValue":true,"conditions":[
                {"variable":"array","path":[0],"operator":"equals","value":"first"}
              ]},
              "length":{"type":"boolean","defaultValue":true,"conditions":[
                {"variable":"array","path":["length"],"operator":"equals","value":1.0}
              ]},
              "numericObject":{"type":"boolean","defaultValue":true,"disabledValue":false,"conditions":[
                {"variable":"object","path":[0],"operator":"equals","value":"first"}
              ]}
            }}
            """);

        assertEquals(Boolean.TRUE, f.getVariable("stringIndex"));
        assertEquals(Boolean.TRUE, f.getVariable("numericIndex"));
        assertEquals(Boolean.TRUE, f.getVariable("length"));
        assertEquals(Boolean.FALSE, f.getVariable("numericObject"));
    }

    @Test void falseRuleAndVariationSelectorsSkipPrerequisites() throws Exception {
        AtomicInteger sourceCalls = new AtomicInteger();
        FeaturevisorModule module = new FeaturevisorModule("order")
            .beforeEvaluation(options -> {
                if ("source".equals(options.getVariableKey())) sourceCalls.incrementAndGet();
                return options;
            });
        Featurevisor f = instance("""
            {"schemaVersion":"2","revision":"order","segments":{},"features":{"subject":{"bucketBy":"userId","variablesSchema":{"ruleValue":{"type":"string","defaultValue":"default"},"variationValue":{"type":"string","defaultValue":"default"}},"variations":[{"value":"control","variableOverrides":{"variationValue":[{"conditions":[{"attribute":"country","operator":"equals","value":"nl"},{"variable":"source","operator":"equals","value":true}],"value":"wrong"}]}}],"traffic":[{"key":"all","segments":"*","percentage":100000,"variation":"control","variableOverrides":{"ruleValue":[{"conditions":[{"attribute":"country","operator":"equals","value":"nl"},{"variable":"source","operator":"equals","value":true}],"value":"wrong"}]}}]}},"variables":{"source":{"type":"boolean","defaultValue":true}}}
            """, module);
        Map<String, Object> context = Map.of("country", "de");

        assertEquals("default", f.getVariableString("subject", "ruleValue", context, null));
        assertEquals("default", f.getVariableString("subject", "variationValue", context, null));
        assertEquals(0, sourceCalls.get());
    }

    @Test void publicCompatibilityAliasesAreAbsent() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> Feature.class.getMethod("getRequired"));
        assertThrows(NoSuchMethodException.class, () -> Evaluation.class.getMethod("getSticky"));
        assertThrows(NoSuchMethodException.class, () -> FeaturevisorModule.class.getMethod("before", java.util.function.Function.class));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.featurevisor.sdk.RequiredWithVariation"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.featurevisor.sdk.VariableKey"));
    }
}
