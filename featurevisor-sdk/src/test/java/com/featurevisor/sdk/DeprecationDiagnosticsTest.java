package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DeprecationDiagnosticsTest {
    @Test
    void reportsDeprecatedFeaturesAndVariablesWithOneCodePerKind() throws Exception {
        List<String> captured = new ArrayList<>();
        Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson("{\"schemaVersion\":\"4\",\"revision\":\"1\",\"segments\":{},"
                + "\"features\":{\"legacy\":{\"deprecated\":true,\"bucketBy\":\"userId\","
                + "\"variablesSchema\":{\"old\":{\"type\":\"string\",\"defaultValue\":\"x\",\"deprecated\":true}},"
                + "\"traffic\":[{\"key\":\"all\",\"conditions\":\"*\",\"percentage\":100000,\"allocation\":[]}]}},"
                + "\"variables\":{\"oldGlobal\":{\"type\":\"string\",\"defaultValue\":\"y\",\"deprecated\":true}}}"))
            .logLevel(FeaturevisorLogLevel.WARN)
            .onDiagnostic(diagnostic -> {
                if (diagnostic.getCode().contains("deprecated")) captured.add(diagnostic.getCode());
            }));
        try {
            sdk.isEnabled("legacy");
            assertEquals(List.of("deprecated_feature"), new ArrayList<>(new TreeSet<>(captured)));

            captured.clear();
            sdk.getVariable("legacy", "old", new HashMap<>());
            assertEquals(List.of("deprecated_feature", "deprecated_variable"), new ArrayList<>(new TreeSet<>(captured)));

            captured.clear();
            sdk.getVariable("oldGlobal");
            assertEquals(List.of("deprecated_variable"), captured);
        } finally {
            sdk.close();
        }
    }
}
