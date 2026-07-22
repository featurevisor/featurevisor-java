package com.featurevisor.openfeature;

import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorLogLevel;
import com.featurevisor.sdk.FeaturevisorModule;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeaturevisorOpenFeatureProviderTest {
    private static final String DATAFILE = """
        {"schemaVersion":"2","revision":"openfeature-test","segments":{},"features":{"checkout":{
          "bucketBy":"userId",
          "variations":[{"value":"on","variables":{"title":"Hello","count":3,"ratio":1.5,"visible":true,"items":["a"],"config":{"color":"blue"},"json":"{\\\"nested\\\":true}"}}],
          "variablesSchema":{"title":{"type":"string","defaultValue":"Default"},"count":{"type":"integer","defaultValue":0},"ratio":{"type":"double","defaultValue":0},"visible":{"type":"boolean","defaultValue":false},"items":{"type":"array","defaultValue":[]},"config":{"type":"object","defaultValue":{}},"json":{"type":"json","defaultValue":"{}"}},
          "force":[{"conditions":{"attribute":"userId","operator":"equals","value":"forced-user"},"enabled":true,"variation":"on"}],
          "traffic":[{"key":"all","segments":"*","percentage":100000,"variation":"on"}]
        }}}""";

    private Featurevisor.FeaturevisorOptions options() throws Exception {
        return new Featurevisor.FeaturevisorOptions().datafile(DatafileContent.fromJson(DATAFILE)).logLevel(FeaturevisorLogLevel.FATAL);
    }

    @AfterEach void closeOpenFeature() { OpenFeatureAPI.getInstance().shutdown(); }

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
}
