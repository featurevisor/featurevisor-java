package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StackDepthTest {
    private static String chain(int length) {
        StringBuilder features = new StringBuilder();
        for (int index = 0; index < length; index++) {
            if (index > 0) features.append(',');
            String conditions = index == 0 ? "\"*\"" : "\"[{\\\"feature\\\":\\\"f" + (index - 1) + "\\\"}]\"";
            features.append("\"f").append(index).append("\":{\"bucketBy\":\"userId\",\"traffic\":[{\"key\":\"all\",\"conditions\":")
                .append(conditions).append(",\"percentage\":100000}]}");
        }
        return "{\"schemaVersion\":\"4\",\"revision\":\"depth\",\"segments\":{},\"features\":{" + features + "}}";
    }

    private static Featurevisor create(List<String> errors, FeaturevisorModule... modules) {
        return Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(chain(300)).logLevel(FeaturevisorLogLevel.ERROR).modules(List.of(modules))
            .onDiagnostic(diagnostic -> {
                if (diagnostic.getLevel() == FeaturevisorLogLevel.ERROR) {
                    synchronized (errors) { errors.add(diagnostic.getCode()); }
                }
            }));
    }

    private static Object onThread(long stackSize, java.util.function.Supplier<Object> body) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try { result.set(body.get()); } catch (Throwable thrown) { result.set(thrown); }
        }, "small-stack", stackSize);
        thread.start();
        thread.join();
        return result.get();
    }

    @Test void smallThreadStacksNeverThrowFromGetters() throws Exception {
        List<String> errors = new ArrayList<>();
        Featurevisor f = create(errors);
        try {
            Map<String, Object> context = Map.of("userId", "u");
            assertEquals(Boolean.TRUE, onThread(512 * 1024, () -> f.isEnabled("f32", context)));
            // Whether 256 handoffs fit in a 256 KB stack depends on the platform, JDK and JIT.
            // Either way the getter returns a value, and a failure is reported once.
            Object small = onThread(256 * 1024, () -> f.isEnabled("f256", context));
            assertTrue(small instanceof Boolean, () -> "getter threw " + small);
            List<String> expected = Boolean.TRUE.equals(small) ? List.of() : List.of("evaluation_error");
            assertEquals(expected, errors);
            assertTrue(f.isEnabled("f256", context));
            assertEquals(expected, errors);
        } finally { f.close(); }
    }

    @Test void stackOverflowInADependencyFailsTheWholeCallOnce() {
        List<String> errors = new ArrayList<>();
        // Overflow where a deep chain would, at its innermost dependency, on every platform.
        FeaturevisorModule overflow = new FeaturevisorModule("overflow").beforeEvaluation(options -> {
            if ("f0".equals(options.getFeatureKey())) throw new StackOverflowError();
            return options;
        });
        Featurevisor f = create(errors, overflow);
        try {
            Map<String, Object> context = Map.of("userId", "u");
            assertFalse(assertDoesNotThrow(() -> f.isEnabled("f256", context)));
            assertEquals(List.of("evaluation_error"), errors);
        } finally { f.close(); }
    }

    @Test void dependencyFailuresReportOnceForEachPublicCall() {
        List<String> errors = new ArrayList<>();
        Featurevisor f = create(errors);
        try {
            Map<String, Object> context = Map.of("userId", "u");
            assertFalse(f.isEnabled("f299", context));
            assertNull(f.getVariation("f299", context));
            assertEquals(List.of("evaluation_error", "evaluation_error"), errors);
        } finally { f.close(); }
    }
}
