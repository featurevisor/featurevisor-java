package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SetDatafileResultTest {
    private static final String ACCEPTED = """
        {"schemaVersion":"2","revision":"accepted","segments":{},
         "features":{"flag":{"key":"flag","hash":"stable","bucketBy":"userId",
             "traffic":[{"key":"all","segments":"*","percentage":100000,"enabled":true}]}},
         "variables":{"colour":{"key":"colour","hash":"stable","type":"string","defaultValue":"blue"}}}
        """;

    private static boolean set(Featurevisor sdk, Object input, Boolean replace) {
        if (input instanceof String) {
            return replace == null ? sdk.setDatafile((String) input) : sdk.setDatafile((String) input, replace);
        }
        return replace == null ? sdk.setDatafile((DatafileContent) input) : sdk.setDatafile((DatafileContent) input, replace);
    }

    @Test void acceptanceAcrossOverloadsAndModes() throws Exception {
        for (Object input : new Object[]{ACCEPTED, DatafileContent.fromJson(ACCEPTED)}) {
            for (Boolean replace : new Boolean[]{null, false, true}) {
                List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
                Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafileString(ACCEPTED.replace("flag", "retainedFlag").replace("colour", "retainedColour").replace("accepted", "initial"))
                    .onDiagnostic(diagnostics::add));
                try {
                    List<FeaturevisorEventDetails> events = new ArrayList<>();
                    sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
                    assertTrue(set(sdk, input, replace));
                    assertTrue(set(sdk, input, replace), "Unchanged datafiles are still accepted");
                    assertEquals(2, events.size());
                    FeaturevisorEventDetails unchanged = events.get(1);
                    assertEquals(false, unchanged.get("revisionChanged"));
                    assertEquals(Boolean.TRUE.equals(replace), unchanged.get("replaced"));
                    assertEquals(List.of(), unchanged.get("features"));
                    assertEquals(List.of(), unchanged.get("variables"));
                    assertEquals("datafile_set", diagnostics.get(diagnostics.size() - 1).getCode());
                    assertTrue(sdk.isEnabled("flag"));
                    assertEquals("blue", sdk.getVariableString("colour"));
                    assertEquals(!Boolean.TRUE.equals(replace), sdk.getFeature("retainedFlag") != null);
                    assertEquals(!Boolean.TRUE.equals(replace), sdk.getVariableString("retainedColour") != null);

                    sdk.close();
                    int before = diagnostics.size();
                    assertFalse(set(sdk, input, replace));
                    assertFalse(set(sdk, "{", replace));
                    assertFalse(sdk.setDatafile((String) null));
                    assertFalse(sdk.setDatafile((String) null, true));
                    assertFalse(sdk.setDatafile((DatafileContent) null));
                    assertFalse(sdk.setDatafile((DatafileContent) null, true));
                    assertEquals(before, diagnostics.size());
                    assertEquals(2, events.size());
                    assertEquals("accepted", sdk.getRevision());
                } finally {
                    sdk.close();
                }
            }
        }
    }

    @Test void rejectionPreservesStateDiagnosticsAndEventBaseline() throws Exception {
        for (Object input : new Object[]{"{", "null", "[]", "{}", null, new DatafileContent(),
                "{\"schemaVersion\":\"2\",\"revision\":\"bad\",\"segments\":null,\"features\":{}}"}) {
            for (Boolean replace : new Boolean[]{null, false, true}) {
                List<FeaturevisorDiagnostic> diagnostics = new ArrayList<>();
                Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                    .datafile(DatafileContent.fromJson(ACCEPTED)).onDiagnostic(diagnostics::add));
                try {
                    diagnostics.clear();
                    List<FeaturevisorEventDetails> events = new ArrayList<>();
                    AtomicInteger errors = new AtomicInteger();
                    sdk.on(FeaturevisorEventName.DATAFILE_SET, events::add);
                    sdk.on(FeaturevisorEventName.ERROR, event -> {
                        errors.incrementAndGet();
                        assertEquals("invalid_datafile", ((FeaturevisorDiagnostic) event.get("diagnostic")).getCode());
                    });
                    assertFalse(set(sdk, input, replace));
                    assertEquals(1, diagnostics.size());
                    assertEquals("invalid_datafile", diagnostics.get(0).getCode());
                    assertNotNull(diagnostics.get(0).getOriginalError());
                    assertEquals(1, errors.get());
                    assertTrue(events.isEmpty());
                    assertEquals("accepted", sdk.getRevision());
                    assertTrue(sdk.isEnabled("flag"));
                    assertEquals("blue", sdk.getVariableString("colour"));

                    assertTrue(set(sdk, ACCEPTED, replace));
                    assertEquals(1, events.size());
                    assertEquals("accepted", events.get(0).get("previousRevision"));
                    assertEquals(false, events.get(0).get("revisionChanged"));
                    assertEquals(List.of(), events.get(0).get("features"));
                    assertEquals(List.of(), events.get(0).get("variables"));
                } finally {
                    sdk.close();
                }
            }
        }
    }

    @Test void nullStringsAreRejectedByBothOverloads() {
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().logLevel(FeaturevisorLogLevel.FATAL));
        try {
            assertFalse(sdk.setDatafile((String) null));
            assertFalse(sdk.setDatafile((String) null, true));
        } finally {
            sdk.close();
        }
    }

    @Test void callbackFailuresDoNotChangeAcceptance() {
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .onDiagnostic(diagnostic -> { throw new IllegalStateException("diagnostic"); }));
        try {
            AtomicInteger calls = new AtomicInteger();
            sdk.on(FeaturevisorEventName.DATAFILE_SET, event -> { throw new IllegalStateException("listener"); });
            sdk.on(FeaturevisorEventName.DATAFILE_SET, event -> calls.incrementAndGet());
            sdk.on(FeaturevisorEventName.ERROR, event -> { throw new IllegalStateException("error listener"); });
            assertTrue(sdk.setDatafile(ACCEPTED));
            assertFalse(sdk.setDatafile("{"));
            assertEquals(1, calls.get());
            assertEquals("accepted", sdk.getRevision());
        } finally {
            sdk.close();
        }
    }

    @Test void resultBelongsToItsOwnCallWhenListenersReenterOrClose() {
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().logLevel(FeaturevisorLogLevel.FATAL));
        sdk.on(FeaturevisorEventName.DATAFILE_SET, event -> {
            assertFalse(sdk.setDatafile("{"));
            sdk.close();
        });
        assertTrue(sdk.setDatafile(ACCEPTED));
        assertFalse(sdk.setDatafile(ACCEPTED));

        Featurevisor recovered = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().logLevel(FeaturevisorLogLevel.FATAL));
        try {
            recovered.on(FeaturevisorEventName.ERROR, event -> assertTrue(recovered.setDatafile(ACCEPTED)));
            assertFalse(recovered.setDatafile("{"));
            assertEquals("accepted", recovered.getRevision());
        } finally {
            recovered.close();
        }
    }

    @Test void diagnosticCallbackCanCloseWithoutChangingAcceptance() {
        Featurevisor[] reference = new Featurevisor[1];
        reference[0] = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions().onDiagnostic(diagnostic -> {
            if ("datafile_set".equals(diagnostic.getCode())) reference[0].close();
        }));
        assertTrue(reference[0].setDatafile(ACCEPTED));
        assertFalse(reference[0].setDatafile(ACCEPTED));
        assertEquals("accepted", reference[0].getRevision());
    }
}
