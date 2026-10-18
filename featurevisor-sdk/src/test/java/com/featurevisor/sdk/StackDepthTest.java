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

    private static Featurevisor create(List<String> errors) {
        return Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafileString(chain(300)).logLevel(FeaturevisorLogLevel.ERROR).onDiagnostic(diagnostic -> {
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

    @Test void smallThreadStacksFailDeepChainsWithoutThrowingFromGetters() throws Exception {
        List<String> errors = new ArrayList<>();
        Featurevisor f = create(errors);
        try {
            Map<String, Object> context = Map.of("userId", "u");
            assertEquals(Boolean.TRUE, onThread(512 * 1024, () -> f.isEnabled("f32", context)));
            assertEquals(Boolean.FALSE, onThread(256 * 1024, () -> f.isEnabled("f256", context)));
            assertTrue(f.isEnabled("f256", context));
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
