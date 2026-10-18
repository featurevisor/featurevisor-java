package com.featurevisor.sdk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class InitialLoadingTest {
    private static final String INITIAL = """
        {"schemaVersion":"4","revision":"initial",
         "segments":{"audience":{"conditions":"{\\\"variable\\\":\\\"source\\\",\\\"operator\\\":\\\"notNull\\\"}"},
                     "alias":{"conditions":"audience"},"broken":{"conditions":"{bad"}},
         "features":{"gate":{"hash":"stable","bucketBy":"userId","conditions":"alias",
             "traffic":[{"key":"all","conditions":"*","percentage":100000}]}},
         "variables":{"source":{"hash":"one","type":"integer","defaultValue":1},
                      "map":{"hash":"stable","type":"object","compose":[{"key":"enabled","feature":"gate"}]},
                      "alias":{"hash":"stable","type":"object","compose":"map"}}}
        """;

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void assertCold(Featurevisor sdk) throws Exception {
        Object provider = field(sdk, "evaluationData");
        for (String name : List.of("normalizedFeatures", "normalizedSegments", "normalizedVariables")) {
            for (Object slot : ((Map<?, ?>) field(provider, name)).values()) assertNull(field(slot, "value"));
        }
    }

    private static void assertAffected(FeaturevisorEventDetails event, Set<String> variables) {
        assertEquals(Set.of("gate"), Set.copyOf((List<?>) event.get("features")));
        assertEquals(variables, Set.copyOf((List<?>) event.get("variables")));
    }

    @Test void initialEventDoesNotReadSelectorsButSegmentOnlyPreviousStateDoes() {
        AtomicInteger reads = new AtomicInteger();
        Feature probe = new Feature() {
            @Override public Object getConditions() {
                reads.incrementAndGet();
                return "audience";
            }
        };
        GlobalVariable value = new GlobalVariable();
        DatafileContent previous = new DatafileContent("4", "empty");
        previous.setFeatures(Map.of()); previous.setSegments(Map.of()); previous.setVariables(Map.of());
        DatafileContent incoming = new DatafileContent("4", "initial");
        incoming.setFeatures(Map.of("gate", probe)); incoming.setVariables(Map.of("source", value));
        incoming.setSegments(Map.of());
        for (boolean replace : List.of(false, true)) {
            FeaturevisorEventDetails event = Events.getParamsForDatafileSetEvent(previous, incoming, replace);
            assertAffected(event, Set.of("source"));
            assertEquals("empty", event.get("previousRevision"));
            assertEquals("initial", event.get("revision"));
            assertEquals(true, event.get("revisionChanged"));
            assertEquals(replace, event.get("replaced"));
            assertEquals(0, reads.get(), "Initial notification must not build the dependency index");
        }
        Segment segment = new Segment(); segment.setConditions("*");
        previous.setSegments(Map.of("audience", segment));
        Events.getParamsForDatafileSetEvent(previous, incoming, false);
        assertTrue(reads.get() > 0, "A segment only previous datafile must use the update path");
    }

    @Test void firstPartialUpdateBuildsGraphsWithoutWarmingEntitiesOrDiagnostics() throws Exception {
        List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(INITIAL).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add));
        try {
            assertCold(sdk);
            assertTrue(diagnostics.isEmpty());
            List<FeaturevisorEventDetails> events = new ArrayList<>();
            sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
            assertTrue(sdk.setDatafile("""
                {"schemaVersion":"4","revision":"partial","segments":{},"features":{},
                 "variables":{"source":{"hash":"two","type":"integer","defaultValue":2}}}
                """));
            assertAffected(events.get(0), Set.of("source", "map", "alias"));
            assertCold(sdk);
            assertTrue(diagnostics.isEmpty());
            assertTrue(sdk.isEnabled("gate"));
            assertEquals(Map.of("enabled", true), sdk.getVariable("alias"));
            sdk.getSegment("broken"); sdk.getSegment("broken");
            assertEquals(1, diagnostics.size());
            assertEquals("conditions_parse_error", diagnostics.get(0).getCode());
            assertEquals("broken", diagnostics.get(0).getDetails().get("entityKey"));
        } finally { sdk.close(); }
    }

    @Test void firstUpdateUsesNewEdgesAndReplacementUsesRemovedEdges() throws Exception {
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(INITIAL).logLevel(FeaturevisorLogLevel.FATAL));
        try {
            List<FeaturevisorEventDetails> events = new ArrayList<>();
            sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
            assertTrue(sdk.setDatafile("""
                {"schemaVersion":"4","revision":"partial","segments":{},
                 "features":{"gate":{"hash":"stable","conditions":{"variable":"added","operator":"notNull"}}},
                 "variables":{"added":{"hash":"new","type":"integer","defaultValue":2}}}
                """));
            assertAffected(events.get(0), Set.of("added", "map", "alias"));
        } finally { sdk.close(); }

        sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(INITIAL).logLevel(FeaturevisorLogLevel.FATAL));
        try {
            List<FeaturevisorEventDetails> events = new ArrayList<>();
            sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
            DatafileContent replacement = DatafileContent.fromJson(INITIAL);
            replacement.getVariables().remove("source");
            replacement.getFeatures().get("gate").setConditions("*");
            assertTrue(sdk.setDatafile(replacement, true));
            assertAffected(events.get(0), Set.of("source", "map", "alias"));
        } finally { sdk.close(); }
    }
}
