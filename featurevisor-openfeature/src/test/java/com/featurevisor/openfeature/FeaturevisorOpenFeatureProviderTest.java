package com.featurevisor.openfeature;

import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Evaluation;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import com.featurevisor.sdk.FeaturevisorModule;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.Structure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeaturevisorOpenFeatureProviderTest {
    @Test void clientDefaultsTrackingAndUpdatesWithBorrowedLifecycle() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Featurevisor featurevisor = Featurevisor.createFeaturevisor(options().modules(List.of(
            new FeaturevisorModule("owner").close(() -> closed.set(true)))));
        List<String> tracked = new ArrayList<>();
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisor(featurevisor)
                .onTrack((name, context, details) -> tracked.add(name + ":" + context.getTargetingKey())));
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        try {
            api.setProviderAndWait(provider);
            var client = api.getClient();
            var error = new java.util.concurrent.CountDownLatch(1);
            var errorCode = new java.util.concurrent.atomic.AtomicReference<ErrorCode>();
            client.onProviderError(event -> {
                errorCode.set(event.getErrorCode());
                error.countDown();
            });
            var context = new ImmutableContext("forced-user");
            var result = client.getBooleanDetails("checkout", false, context);
            assertTrue(result.getValue());
            assertEquals(Reason.TARGETING_MATCH.name(), result.getReason());
            var missing = client.getStringDetails("variable:missing", "fallback", context);
            assertEquals("fallback", missing.getValue());
            assertEquals(ErrorCode.FLAG_NOT_FOUND, missing.getErrorCode());
            client.track("purchase", context);
            assertEquals(List.of("purchase:forced-user"), tracked);
            assertTrue(featurevisor.setDatafile(DATAFILE.replace("Hello", "Updated"), true));
            assertEquals("Updated", client.getStringValue("checkout:title", "fallback", context));
            assertFalse(featurevisor.setDatafile("{", true));
            assertTrue(error.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(ErrorCode.PARSE_ERROR, errorCode.get());
            var ready = new java.util.concurrent.CountDownLatch(1);
            client.onProviderReady(event -> ready.countDown());
            assertTrue(featurevisor.setDatafile(DATAFILE, true));
            assertTrue(ready.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(client.getBooleanValue("checkout", false, context));
            api.shutdown();
            assertFalse(closed.get());
        } finally {
            featurevisor.close();
        }
        assertTrue(closed.get());
    }

    @Test void invalidRegistrationAndRecoveryThroughClient() throws Exception {
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(new Featurevisor.FeaturevisorOptions()
                .datafileString("{}").logLevel(FeaturevisorLogLevel.FATAL));
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        assertThrows(Exception.class, () -> api.setProviderAndWait(provider));
        assertEquals(dev.openfeature.sdk.ProviderState.ERROR, provider.getState());
        provider.getFeaturevisor().setDatafile(DATAFILE, true);
        assertEquals(dev.openfeature.sdk.ProviderState.READY, provider.getState());
        assertTrue(api.getClient().getBooleanValue("checkout", false));
    }

    @Test void initialHealthIgnoresLogFilteringAndRecovers() throws Exception {
        for (FeaturevisorLogLevel level : List.of(FeaturevisorLogLevel.ERROR, FeaturevisorLogLevel.FATAL)) {
            for (String input : List.of("{", "null", "{}", "{\"schemaVersion\":\"2\",\"revision\":\"x\",\"features\":{},\"segments\":[]}")) {
                List<String> diagnostics = new ArrayList<>();
                List<String> revisions = new ArrayList<>();
                Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
                        .datafileString(input).logLevel(level).onDiagnostic(d -> diagnostics.add(d.getCode()))
                        .modules(List.of(new FeaturevisorModule("setup").setup(module -> revisions.add(module.getRevision()))));
                com.featurevisor.sdk.FeaturevisorDiagnosticHandler handler = options.getOnDiagnostic();
                FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(options);
                assertEquals(List.of("unknown"), revisions);
                assertSame(handler, options.getOnDiagnostic());
                assertEquals(input, options.getDatafileString());
                assertEquals(dev.openfeature.sdk.ProviderState.ERROR, provider.getState(), input + " at " + level);
                assertThrows(dev.openfeature.sdk.exceptions.ParseError.class, () -> provider.initialize(ImmutableContext.EMPTY));
                assertEquals(ErrorCode.PARSE_ERROR, provider.getBooleanEvaluation("checkout", false, ImmutableContext.EMPTY).getErrorCode());
                assertEquals(level == FeaturevisorLogLevel.FATAL ? 0 : 1, diagnostics.stream().filter("invalid_datafile"::equals).count());
                provider.getFeaturevisor().setDatafile(DATAFILE, true);
                provider.initialize(ImmutableContext.EMPTY);
                assertEquals(dev.openfeature.sdk.ProviderState.READY, provider.getState());
                provider.shutdown();
            }
        }
        FeaturevisorOpenFeatureProvider typed = new FeaturevisorOpenFeatureProvider(new Featurevisor.FeaturevisorOptions()
                .datafile(new DatafileContent()).logLevel(FeaturevisorLogLevel.FATAL));
        assertEquals(ErrorCode.PARSE_ERROR, typed.getBooleanEvaluation("checkout", false, ImmutableContext.EMPTY).getErrorCode());
        typed.shutdown();
    }

    @Test void clientPreservesExplicitEmptyTargetingKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode fixture = mapper.readTree(FeaturevisorOpenFeatureProviderTest.class.getResourceAsStream("/openfeature-provider.json"));
        Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson(fixture.path("datafile").toString())).logLevel(FeaturevisorLogLevel.FATAL);
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(new FeaturevisorOpenFeatureProvider(options));
        Map<String, Value> user = Map.of("userId", new Value("forced"));
        assertTrue(api.getClient().getBooleanValue("targetOnly", false, new ImmutableContext(user)));
        assertFalse(api.getClient().getBooleanValue("targetOnly", false, new ImmutableContext("", user)));
        assertFalse(api.getClient().getBooleanValue("targetOnly", false, new ImmutableContext("other", user)));
        assertEquals("forced", user.get("userId").asString());
        api.setProviderAndWait(new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisorOptions(options).targetingKeyField("accountId")));
        Map<String, Value> account = Map.of("accountId", new Value("old"));
        assertTrue(api.getClient().getBooleanValue("accountTargeted", false, new ImmutableContext("", account)));
        assertEquals("old", account.get("accountId").asString());
    }

    @Test void sharedProviderConformance() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode fixture = mapper.readTree(FeaturevisorOpenFeatureProviderTest.class.getResourceAsStream("/openfeature-provider.json"));
        assertEquals("featurevisor.openfeature-provider", fixture.path("kind").asText());
        assertEquals(1, fixture.path("schemaVersion").asInt());
        assertEquals(16, fixture.path("cases").size());
        Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson(fixture.path("datafile").toString())).logLevel(FeaturevisorLogLevel.FATAL);
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(options);
        for (JsonNode item : fixture.path("cases")) {
            String name = item.path("name").asText();
            JsonNode expected = item.path("expected");
            String source = item.path("context").toString();
            ImmutableContext context = corpusContext(item.path("context"), mapper);
            ProviderEvaluation<?> result;
            String key = item.path("flagKey").asText();
            switch (item.path("type").asText()) {
                case "boolean" -> result = provider.getBooleanEvaluation(key, item.path("default").asBoolean(), context);
                case "string" -> result = provider.getStringEvaluation(key, item.path("default").asText(), context);
                case "object" -> {
                    Map<String, Object> fallback = mapper.convertValue(item.path("default"), Map.class);
                    result = provider.getObjectEvaluation(key, new Value(Structure.mapToStructure(fallback)), context);
                }
                default -> throw new AssertionError("unknown case type " + name);
            }
            if ("object".equals(item.path("type").asText())) {
                Map<String, Object> want = mapper.convertValue(expected.path("value"), Map.class);
                assertEquals(want, ((Value) result.getValue()).asStructure().asObjectMap(), name);
            } else if ("boolean".equals(item.path("type").asText())) {
                assertEquals(expected.path("value").asBoolean(), result.getValue(), name);
            } else {
                assertEquals(expected.path("value").asText(), result.getValue(), name);
            }
            if (expected.has("reason")) assertEquals(expected.path("reason").asText(), result.getReason(), name);
            assertEquals(expected.has("variant") ? expected.path("variant").asText() : null, result.getVariant(), name);
            assertEquals(expected.has("errorCode") ? ErrorCode.valueOf(expected.path("errorCode").asText()) : null, result.getErrorCode(), name);
            assertEquals(source, item.path("context").toString(), name);
        }
        for (JsonNode item : fixture.path("targetingCases")) {
            String source = item.path("context").toString();
            assertEquals(item.path("expectedValue").asBoolean(), provider.getBooleanEvaluation("targetOnly", false, corpusContext(item.path("context"), mapper)).getValue(), item.path("name").asText());
            assertEquals(source, item.path("context").toString());
        }
        provider.shutdown();
        FeaturevisorOpenFeatureProvider custom = new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisorOptions(options).targetingKeyField("accountId"));
        JsonNode item = fixture.path("customTargetingCase");
        assertEquals(item.path("expectedValue").asBoolean(), custom.getBooleanEvaluation(item.path("flagKey").asText(), false, corpusContext(item.path("context"), mapper)).getValue());
        custom.shutdown();
    }

    private static ImmutableContext corpusContext(JsonNode source, ObjectMapper mapper) {
        Map<String, Value> fields = new HashMap<>();
        source.fields().forEachRemaining(entry -> {
            if (!entry.getKey().equals("targetingKey")) fields.put(entry.getKey(), Value.objectToValue(mapper.convertValue(entry.getValue(), Object.class)));
        });
        return new ImmutableContext(source.has("targetingKey") ? source.path("targetingKey").asText() : null, fields);
    }

    private static final String DATAFILE = """
        {"schemaVersion":"2","revision":"openfeature-test","segments":{},"variables":{"welcome":{"hash":"welcome","type":"string","defaultValue":"Welcome","overrides":[{"key":"nl","conditions":{"attribute":"country","operator":"equals","value":"nl"},"value":"Welkom"}]}},"features":{"checkout":{
          "bucketBy":"userId",
          "variations":[{"value":"on","variables":{"title":"Hello","count":3,"ratio":1.5,"visible":true,"items":["a"],"config":{"color":"blue"},"json":"{\\\"nested\\\":true}"}}],
          "variablesSchema":{"title":{"type":"string","defaultValue":"Default"},"count":{"type":"integer","defaultValue":0},"ratio":{"type":"double","defaultValue":0},"visible":{"type":"boolean","defaultValue":false},"items":{"type":"array","defaultValue":[]},"config":{"type":"object","defaultValue":{}},"json":{"type":"json","defaultValue":"{}"}},
          "force":[{"conditions":{"attribute":"userId","operator":"equals","value":"forced-user"},"enabled":true,"variation":"on"}],
          "traffic":[{"key":"all","segments":"*","percentage":100000,"variation":"on"}]
        }}}""";

    private Featurevisor.FeaturevisorOptions options() throws Exception {
        return new Featurevisor.FeaturevisorOptions().datafile(DatafileContent.fromJson(DATAFILE)).logLevel(FeaturevisorLogLevel.FATAL);
    }

    private FeaturevisorOpenFeatureProvider providerWithCount(Number value) throws Exception {
        FeaturevisorModule module = new FeaturevisorModule("integer-value").afterEvaluation((evaluation, evaluateOptions) -> {
            if ("count".equals(evaluation.getVariableKey())) evaluation.setVariableValue(value);
            return evaluation;
        });
        return new FeaturevisorOpenFeatureProvider(options().modules(List.of(module)));
    }

    @AfterEach void closeOpenFeature() { OpenFeatureAPI.getInstance().shutdown(); }

    @Test void borrowedInstanceParseErrorRecoveryAndCleanup() throws Exception {
        Featurevisor f = Featurevisor.createFeaturevisor(options());
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisor(f));
        assertFalse(f.setDatafile("{", true));
        assertEquals(ErrorCode.PARSE_ERROR, provider.getBooleanEvaluation("checkout", false, null).getErrorCode());
        assertTrue(f.setDatafile(DATAFILE, true));
        assertNull(provider.getBooleanEvaluation("checkout", false, null).getErrorCode());
        provider.shutdown();
        assertFalse(f.setDatafile("{", true));
        assertNull(provider.getBooleanEvaluation("checkout", false, null).getErrorCode());
        f.close();
    }

    @Test void resolvesEveryTypeAndMapsTargetingKey() throws Exception {
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(options());
        ImmutableContext context = new ImmutableContext("forced-user");
        assertTrue(provider.getBooleanEvaluation("checkout", false, context).getValue());
        assertEquals("on", provider.getStringEvaluation("checkout:variation", "fallback", context).getValue());
        assertEquals("Hello", provider.getStringEvaluation("checkout:title", "fallback", context).getValue());
        assertEquals(3, provider.getIntegerEvaluation("checkout:count", 0, context).getValue());
        assertEquals(1.5, provider.getDoubleEvaluation("checkout:ratio", 0.0, context).getValue());
        assertTrue(provider.getBooleanEvaluation("checkout:visible", false, context).getValue());
        assertEquals("a", provider.getObjectEvaluation("checkout:items", new Value(new ArrayList<Value>()), context).getValue().asList().get(0).asString());
        assertEquals("blue", provider.getObjectEvaluation("checkout:config", new Value(), context).getValue().asStructure().getValue("color").asString());
        assertTrue(provider.getObjectEvaluation("checkout:json", new Value(), context).getValue().asStructure().getValue("nested").asBoolean());
    }

    @Test void integerResolutionValidatesFiniteIntegralInt32RangeBeforeConversion() throws Exception {
        ImmutableContext context = new ImmutableContext("forced-user");
        for (Number value : List.<Number>of(Integer.MIN_VALUE, Integer.MAX_VALUE, -2147483648.0, 2147483647.0)) {
            ProviderEvaluation<Integer> result = providerWithCount(value).getIntegerEvaluation("checkout:count", 17, context);
            assertEquals(value.intValue(), result.getValue(), value.toString());
            assertNull(result.getErrorCode(), value.toString());
        }

        for (Number value : List.<Number>of(2147483648L, -2147483649L, 1.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            ProviderEvaluation<Integer> result = providerWithCount(value).getIntegerEvaluation("checkout:count", 17, context);
            assertEquals(17, result.getValue(), value.toString());
            assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode(), value.toString());
            assertEquals(Reason.ERROR.name(), result.getReason(), value.toString());
            assertEquals("checkout", result.getFlagMetadata().getString("featureKey"));
            assertEquals("count", result.getFlagMetadata().getString("variableKey"));
            assertEquals(Evaluation.REASON_FORCED, result.getFlagMetadata().getString("featurevisorReason"));
        }
    }

    @Test void handlesErrorsCustomGrammarTrackingAndLifecycle() throws Exception {
        List<String> tracked = new ArrayList<>();
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(
                new FeaturevisorOpenFeatureProvider.Options().featurevisorOptions(options()).keySeparator("/").variationKey("$variation").onTrack((name, context, details) -> tracked.add(name)));
        assertEquals("on", provider.getStringEvaluation("checkout/$variation", "fallback", ImmutableContext.EMPTY).getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, provider.getStringEvaluation("missing", "fallback", ImmutableContext.EMPTY).getErrorCode());
        ProviderEvaluation<Boolean> missing = provider.getBooleanEvaluation("missing", true, ImmutableContext.EMPTY);
        assertTrue(missing.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, missing.getErrorCode());
        provider.track("purchase", ImmutableContext.EMPTY, null);
        assertEquals(List.of("purchase"), tracked);
        provider.shutdown();
    }

    @Test void resolvesGlobalVariablesWithDefaultAndCustomPrefixes() throws Exception {
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(options());
        assertEquals("Welcome", provider.getStringEvaluation("variable:welcome", "fallback", ImmutableContext.EMPTY).getValue());

        FeaturevisorOpenFeatureProvider custom = new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisorOptions(options()).globalVariablePrefix("global")
        );
        ProviderEvaluation<String> result = custom.getStringEvaluation(
            "global:welcome", "fallback", new ImmutableContext("user", java.util.Map.of("country", new Value("nl")))
        );
        assertEquals("Welkom", result.getValue());
        assertEquals("nl", result.getFlagMetadata().getString("variableOverrideKey"));
        assertNull(result.getFlagMetadata().getString("featureKey"));
        assertThrows(IllegalArgumentException.class, () -> new FeaturevisorOpenFeatureProvider(
            new FeaturevisorOpenFeatureProvider.Options().featurevisorOptions(options()).globalVariablePrefix("global:variable")
        ));
    }

    @Test void reportsMalformedDatafileAndWorksThroughOpenFeatureApi() throws Exception {
        FeaturevisorOpenFeatureProvider malformed = new FeaturevisorOpenFeatureProvider(new Featurevisor.FeaturevisorOptions().datafileString("{").logLevel(FeaturevisorLogLevel.FATAL));
        assertEquals(ErrorCode.PARSE_ERROR, malformed.getBooleanEvaluation("checkout", false, ImmutableContext.EMPTY).getErrorCode());
        assertEquals("Could not parse datafile", malformed.getBooleanEvaluation("checkout", false, ImmutableContext.EMPTY).getErrorMessage());
        malformed.getFeaturevisor().setDatafile(DATAFILE, true);
        assertTrue(malformed.getBooleanEvaluation("checkout", false, new ImmutableContext("forced-user")).getValue());

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(new FeaturevisorOpenFeatureProvider(options()));
        assertTrue(api.getClient().getBooleanValue("checkout", false, new ImmutableContext("forced-user")));
    }

    @Test void borrowsExistingFeaturevisor() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        FeaturevisorModule module = new FeaturevisorModule("owner").close(() -> closed.set(true));
        Featurevisor.FeaturevisorOptions featurevisorOptions = options().modules(List.of(module));
        Featurevisor featurevisor = Featurevisor.createFeaturevisor(featurevisorOptions);
        FeaturevisorOpenFeatureProvider provider = new FeaturevisorOpenFeatureProvider(featurevisor);

        assertSame(featurevisor, provider.getFeaturevisor());
        provider.shutdown();
        assertFalse(closed.get());

        featurevisor.close();
        assertTrue(closed.get());
    }

    @Test void mapsUnmetVariableRequirementsToDisabled() throws Exception {
        Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson("""
                {"schemaVersion":"2","revision":"requirements","segments":{},"variables":{
                  "source":{"type":"boolean","defaultValue":false}
                },"features":{
                  "subject":{"bucketBy":"userId","conditions":[{"variable":"source","operator":"equals","value":true}],
                    "traffic":[{"key":"all","segments":"*","percentage":100000}]}
                }}
                """))
            .logLevel(FeaturevisorLogLevel.FATAL);
        ProviderEvaluation<Boolean> result = new FeaturevisorOpenFeatureProvider(options)
            .getBooleanEvaluation("subject", true, ImmutableContext.EMPTY);
        assertFalse(result.getValue());
        assertEquals(Reason.DISABLED.name(), result.getReason());
        assertEquals("conditions_unmet", result.getFlagMetadata().getString("featurevisorReason"));
    }

    @Test void mapsUnmetFeatureRequirementsToDisabled() throws Exception {
        Featurevisor.FeaturevisorOptions options = new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson("""
                {"schemaVersion":"2","revision":"requirements","segments":{},"features":{
                  "subject":{"bucketBy":"userId","requiredFeatures":["missing"],
                    "traffic":[{"key":"all","segments":"*","percentage":100000}]}
                }}
                """))
            .logLevel(FeaturevisorLogLevel.FATAL);
        ProviderEvaluation<Boolean> result = new FeaturevisorOpenFeatureProvider(options)
            .getBooleanEvaluation("subject", true, ImmutableContext.EMPTY);
        assertFalse(result.getValue());
        assertEquals(Reason.DISABLED.name(), result.getReason());
        assertEquals("conditions_unmet", result.getFlagMetadata().getString("featurevisorReason"));
    }
}
