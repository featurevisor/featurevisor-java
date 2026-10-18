package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class WarmEvaluationContractTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testDiagnosticMutationCannotEscapeIntoCachedExpressionsWithIdenticalSource() throws Exception {
        String source = """
            {"attribute":"country","operator":"matches","value":"[",
             "metadata":{"notes":[{"text":"original"}]}}
            """;
        DatafileContent datafile = DatafileContent.fromJson("""
            {"schemaVersion":"2","revision":"diagnostic-ownership","features":{},
             "segments":{"public":{"conditions":"*"}},"variables":{
               "first":{"type":"string","defaultValue":"default","overrides":[
                 {"conditions":"*","value":"wrong"}]},
               "second":{"type":"string","defaultValue":"default","overrides":[
                 {"conditions":"*","value":"wrong"}]}
             }}
            """);
        datafile.getSegments().get("public").setConditions(source);
        datafile.getVariables().values().forEach(variable ->
            variable.getOverrides().get(0).setConditions(source));
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        List<List<Object>> observed = new ArrayList<>();
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile)
            .logLevel(FeaturevisorLogLevel.WARN)
            .onDiagnostic(diagnostic -> {
                if (!"condition_match_error".equals(diagnostic.getCode())) {
                    return;
                }
                diagnostics.add(diagnostic);
                Map<String, Object> condition = (Map<String, Object>) diagnostic.getDetails().get("condition");
                Map<String, Object> metadata = (Map<String, Object>) condition.get("metadata");
                List<Object> notes = (List<Object>) metadata.get("notes");
                Map<String, Object> note = (Map<String, Object>) notes.get(0);
                observed.add(List.of(condition.get("operator"), condition.get("value"), note.get("text")));
                condition.put("operator", "equals");
                condition.put("value", "nl");
                note.put("text", "changed");
                notes.add("changed");
            }));
        Map<String, Object> context = Map.of("country", "nl");

        assertEquals("default", sdk.getVariable("first", context));
        Map<String, Object> publicCondition = (Map<String, Object>) sdk.getSegment("public").getConditions();
        publicCondition.put("operator", "equals");
        publicCondition.put("value", "nl");
        assertEquals("default", sdk.getVariable("second", context));
        assertEquals("default", sdk.getVariable("first", context));
        assertEquals("default", sdk.getVariable("second", context));

        assertEquals(4, diagnostics.size());
        for (int index = 0; index < diagnostics.size(); index++) {
            FeaturevisorDiagnostic diagnostic = diagnostics.get(index);
            assertEquals(List.of("matches", "[", "original"), observed.get(index));
            assertEquals(FeaturevisorLogLevel.WARN, diagnostic.getLevel());
            assertTrue(diagnostic.getOriginalError() instanceof java.util.regex.PatternSyntaxException);
            assertEquals(((Exception) diagnostic.getOriginalError()).getMessage(), diagnostic.getMessage());
            assertEquals(context, diagnostic.getDetails().get("context"));
            assertNotSame(publicCondition, diagnostic.getDetails().get("condition"));
            if (index > 0) {
                assertNotSame(diagnostics.get(index - 1).getDetails().get("condition"),
                    diagnostic.getDetails().get("condition"));
            }
        }
        sdk.close();
    }

    private static DatafileContent datafile() throws Exception {
        return DatafileContent.fromJson("""
            {
              "schemaVersion": "2",
              "revision": "warm-contract",
              "segments": {},
              "features": {
                "enabled": {
                  "bucketBy": "userId",
                  "traffic": [{"key":"all","segments":"*","percentage":100000,"enabled":true}]
                }
              }
            }
            """);
    }

    private static DatafileContent conditionalDatafile(String revision, String country, String value)
            throws Exception {
        return DatafileContent.fromJson(String.format("""
            {
              "schemaVersion":"2",
              "revision":"%s",
              "segments":{},
              "features":{},
              "variables":{
                "message":{
                  "type":"string",
                  "defaultValue":"default",
                  "overrides":[{
                    "conditions":"{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"%s\\"}",
                    "value":"%s"
                  }]
                }
              }
            }
            """, revision, country, value));
    }

    @Test
    public void testDiagnosticSubscriptionAddedByCallbackSeesCurrentEvaluation() throws Exception {
        List<FeaturevisorDiagnostic> received = new ArrayList<>();
        AtomicBoolean subscribed = new AtomicBoolean();
        final FeaturevisorModuleApi[] api = {null};
        FeaturevisorModule module = new FeaturevisorModule("observer")
            .setup(value -> api[0] = value)
            .beforeEvaluation(options -> {
                if (subscribed.compareAndSet(false, true)) {
                    api[0].onDiagnostic(
                        received::add,
                        new FeaturevisorModuleDiagnosticOptions(FeaturevisorLogLevel.DEBUG)
                    );
                }
                return options;
            });
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile())
            .logLevel(FeaturevisorLogLevel.FATAL)
            .modules(List.of(module)));

        sdk.isEnabled("enabled", Map.of("userId", "1"));

        FeaturevisorDiagnostic diagnostic = received.stream()
            .filter(item -> "override from rule".equals(item.getCode()))
            .findFirst()
            .orElseThrow();
        assertEquals(FeaturevisorLogLevel.DEBUG, diagnostic.getLevel());
        assertEquals("override from rule", diagnostic.getMessage());
        assertEquals("enabled", diagnostic.getDetails().get("featureKey"));
        assertTrue(diagnostic.getDetails().containsKey("bucketKey"));
        assertTrue(diagnostic.getDetails().containsKey("bucketValue"));
        sdk.close();
    }

    @Test
    public void testThresholdChangedByCallbackAffectsCurrentEvaluation() throws Exception {
        List<FeaturevisorDiagnostic> received = new ArrayList<>();
        final Featurevisor[] sdk = {null};
        FeaturevisorModule module = new FeaturevisorModule("threshold")
            .beforeEvaluation(options -> {
                sdk[0].setLogLevel(FeaturevisorLogLevel.DEBUG);
                return options;
            });
        sdk[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(datafile())
            .logLevel(FeaturevisorLogLevel.FATAL)
            .onDiagnostic(received::add)
            .modules(List.of(module)));
        received.clear();

        sdk[0].isEnabled("enabled", Map.of("userId", "1"));

        FeaturevisorDiagnostic diagnostic = received.stream()
            .filter(item -> "override from rule".equals(item.getCode()))
            .findFirst()
            .orElseThrow();
        assertEquals(FeaturevisorLogLevel.DEBUG, diagnostic.getLevel());
        assertEquals("enabled", diagnostic.getDetails().get("featureKey"));
        sdk[0].close();
    }

    @Test
    public void testReentrantDatafileReplacementKeepsCurrentGenerationAlive() throws Exception {
        AtomicBoolean replaced = new AtomicBoolean();
        final Featurevisor[] sdk = {null};
        FeaturevisorModule module = new FeaturevisorModule("replacement")
            .beforeEvaluation(options -> {
                if (replaced.compareAndSet(false, true)) {
                    try {
                        sdk[0].setDatafile(conditionalDatafile("new", "de", "germany"), true);
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                }
                return options;
            });
        sdk[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(conditionalDatafile("old", "nl", "netherlands"))
            .logLevel(FeaturevisorLogLevel.FATAL)
            .modules(List.of(module)));

        assertEquals("netherlands", sdk[0].getVariable("message", Map.of("country", "nl")));
        assertEquals("germany", sdk[0].getVariable("message", Map.of("country", "de")));
        sdk[0].close();
    }
}
