package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LazySessionStorageTest {
    @Test void rootBookkeepingDoesNotAllocateDependencyCollections() {
        EvaluationSession session = new EvaluationSession();
        EvaluateOptions root = new EvaluateOptions("flag", "root");
        assertTrue(session.beginRoot(DependencyOf.from(root)));
        session.updateCurrent(DependencyOf.from(new EvaluateOptions("flag", "redirect")));
        assertEquals("redirect", session.current().getFeatureKey());
        assertFalse(session.hasDependencyStorage());
        assertThrows(EvaluationSession.Cycle.class, () -> session.read(
            new EvaluateOptions("flag", "root"), () -> true));
        session.endRoot();
        assertFalse(session.hasDependencyStorage());
        assertNull(session.current());
        assertTrue(session.beginRoot(DependencyOf.from(root)));
        assertTrue(session.segment("segment", () -> true));
        assertTrue(session.hasDependencyStorage());
        session.endRoot();
        assertFalse(session.hasDependencyStorage());
    }

    @Test void capabilityEqualityKeepsJsonTypesButComparesNumbersByValue() throws Exception {
        ObjectMapper json = new ObjectMapper();
        NativeV4CapabilitiesTest.jsonEquals(json.readTree("{\"a\":[1.0]}"), json.readTree("{\"a\":[1]}"), "numeric control");
        NativeV4CapabilitiesTest.jsonEquals(json.readTree("-0.0"), json.readTree("0"), "signed zero");
        for (String[] mismatch : new String[][]{{"1", "true"}, {"null", "false"}, {"\"1\"", "1"}, {"{}", "[]"}, {"{\"a\":null}", "{\"b\":null}"}}) {
            assertThrows(AssertionError.class, () -> NativeV4CapabilitiesTest.jsonEquals(
                json.readTree(mismatch[0]), json.readTree(mismatch[1]), "strict types"));
        }
    }
}
