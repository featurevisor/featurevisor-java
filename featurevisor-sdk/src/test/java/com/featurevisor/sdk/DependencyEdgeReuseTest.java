package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DependencyEdgeReuseTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DatafileContent definitions(String schema) throws Exception {
        return DatafileContent.fromJson("{\"schemaVersion\":\"" + schema + "\",\"revision\":\"one\","
            + "\"segments\":{\"s\":{\"conditions\":\"*\"}},"
            + "\"features\":{\"flag\":{\"hash\":\"flag\",\"conditions\":\"s\",\"bucketBy\":\"userId\",\"traffic\":[]}},"
            + "\"variables\":{\"source\":{\"hash\":\"source\",\"type\":\"string\",\"defaultValue\":\"yes\"},"
            + "\"alias\":{\"hash\":\"alias\",\"type\":\"string\",\"compose\":\"source\"},"
            + "\"broken\":{\"hash\":\"broken\",\"type\":\"string\",\"conditions\":\"[broken\",\"defaultValue\":\"no\"}}}");
    }

    private DatafileContent normalized(String schema) throws Exception {
        return Normalize.incoming(definitions(schema), new ArrayList<>());
    }

    @Test void fullRefreshReconstructsEquivalentEdgesInBothSchemas() throws Exception {
        for (String schema : List.of("2", "4")) for (boolean replace : new boolean[]{false, true}) {
            DatafileContent before = normalized(schema);
            Dependencies.Index old = Dependencies.create(before);
            Dependencies.Index next = Dependencies.update(old, before, normalized(schema));
            assertEquals(old.owners, next.owners);
            DatafileContent changed = normalized(schema);
            changed.getVariables().get("alias").setCompose("other");
            Dependencies.Index refreshed = Dependencies.update(next, before, changed);
            assertEquals(Set.of("1:other"), refreshed.owners.get("1:alias"));
            assertEquals(Set.of("1:source"), old.owners.get("1:alias"));
            Dependencies.Index empty = Dependencies.update(refreshed, changed,
                DatafileContent.fromJson("{\"schemaVersion\":\"" + schema + "\",\"revision\":\"empty\",\"segments\":{},\"features\":{},\"variables\":{}}"));
            assertTrue(empty.owners.isEmpty());
            for (Map<String, Set<String>> reverse : empty.dependents) assertTrue(reverse.isEmpty());
        }
    }

    @Test void fullRefreshDoesNotReuseIncomingDiagnosticSlots() throws Exception {
        for (String schema : List.of("2", "4")) for (boolean replace : new boolean[]{false, true}) {
            List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
            Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(definitions(schema)).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostics::add));
            try {
                assertNull(sdk.getVariable("broken"));
                for (int load = 0; load < 2; load++) {
                    assertTrue(sdk.setDatafile(definitions(schema), replace));
                    assertEquals(load + (schema.equals("2") ? 2 : 1),
                        diagnostics.stream().filter(d -> "conditions_parse_error".equals(d.getCode())).count());
                    assertNull(sdk.getVariable("broken"));
                    assertNull(sdk.getVariable("broken"));
                    assertEquals(load + 2, diagnostics.stream().filter(d -> "conditions_parse_error".equals(d.getCode())).count());
                }
                assertTrue(sdk.setDatafile(DatafileContent.fromJson("{\"schemaVersion\":\"" + schema
                    + "\",\"revision\":\"empty\",\"segments\":{},\"features\":{},\"variables\":{}}"), true));
                assertNull(sdk.getVariable("alias"));
                assertTrue(sdk.getFeatureKeys().isEmpty());
            } finally { sdk.close(); }
        }
    }

    @Test void cyclicHostPayloadsFallBackWithoutAddingSelectorEdges() throws Exception {
        DatafileContent before = definitions("4"), after = definitions("4");
        for (DatafileContent datafile : List.of(before, after)) {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("conditions", payload);
            datafile.getVariables().get("source").setDefaultValue(payload);
        }
        Dependencies.Index next = Dependencies.update(Dependencies.create(before), before, after);
        assertEquals(Set.of("1:alias"), next.dependents[1].get("source"));
        assertFalse(next.dependents[2].containsKey("conditions"));
    }

    @Test void payloadSelectorNamesNeverBecomeEdgesAndHashlessEventsRemainConservative() throws Exception {
        Map<String, Object> input = Map.of("schemaVersion", "4", "revision", "one", "segments", Map.of(),
            "features", Map.of(), "variables", Map.of("value", Map.of("type", "object", "defaultValue",
                Map.of("conditions", "phantom", "compose", "source", "feature", "flag", "variable", "hidden"))));
        DatafileContent before = JSON.convertValue(input, DatafileContent.class);
        Dependencies.Index old = Dependencies.create(before);
        Dependencies.Index next = Dependencies.update(old, before, JSON.convertValue(input, DatafileContent.class));
        for (Map<String, Set<String>> reverse : next.dependents) assertTrue(reverse.isEmpty());
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().datafile(before).onDiagnostic(d -> {}));
        List<FeaturevisorEventDetails> events = new ArrayList<>();
        try {
            sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
            assertTrue(sdk.setDatafile(JSON.convertValue(input, DatafileContent.class)));
            assertEquals(Set.of("value"), new HashSet<>((List<?>) events.get(0).get("variables")));
        } finally { sdk.close(); }
    }
}
