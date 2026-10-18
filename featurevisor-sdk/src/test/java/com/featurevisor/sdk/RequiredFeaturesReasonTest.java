package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RequiredFeaturesReasonTest {
    @Test void unmetFeatureReasonInEvaluationAndCallbacks() throws Exception {
        List<String> observed = new ArrayList<>();
        FeaturevisorModule module = new FeaturevisorModule("observe")
            .afterEvaluation((evaluation, options) -> {
                if ("subject".equals(evaluation.getFeatureKey())) observed.add(evaluation.getReason());
                return evaluation;
            });
        Featurevisor f = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
            .datafile(DatafileContent.fromJson("""
                {"schemaVersion":"2","revision":"reason","segments":{},"features":{
                    "subject":{"bucketBy":"userId","requiredFeatures":["missing"],"traffic":[]}
                }}
                """))
            .logLevel(FeaturevisorLogLevel.FATAL)
            .modules(List.of(module)));
        try {
            Evaluation evaluation = f.evaluateFlag("subject");
            assertEquals(Evaluation.REASON_CONDITIONS_UNMET, evaluation.getReason());
            assertFalse(evaluation.getEnabled());
            assertEquals(List.of("conditions_unmet"), observed);
            var wire = new ObjectMapper().valueToTree(evaluation);
            assertEquals("conditions_unmet", wire.get("reason").asText());
            assertEquals("missing", wire.get("conditions").get(0).get("feature").asText());
            assertThrows(NoSuchFieldException.class, () -> Evaluation.class.getField("REASON_REQUIRED"));
        } finally {
            f.close();
        }
    }
}
