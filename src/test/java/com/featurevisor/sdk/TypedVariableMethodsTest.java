package com.featurevisor.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.featurevisor.sdk.DatafileContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TypedVariableMethodsTest {

    public static class RolloutStep {
        public String name;
        public Integer percentage;
    }

    public static class FeatureConfig {
        public String mode;
        public Integer threshold;
    }

    @Test
    public void testTypedVariableDecoding() throws Exception {
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "test",
              "segments": {},
              "features": {
                "typed": {
                  "key": "typed",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "rollout": {
                      "type": "array",
                      "defaultValue": []
                    },
                    "config": {
                      "type": "object",
                      "defaultValue": {}
                    }
                  },
                  "traffic": [
                    {
                      "key": "rule-1",
                      "segments": "*",
                      "percentage": 100000,
                      "variables": {
                        "rollout": [
                          {"name": "phase-1", "percentage": 10},
                          {"name": "phase-2", "percentage": 80}
                        ],
                        "config": {"mode": "strict", "threshold": 50}
                      }
                    }
                  ]
                }
              }
            }
            """;

        Featurevisor sdk = Featurevisor.createInstance(new Featurevisor.Options().datafile(DatafileContent.fromJson(datafileJson)));
        Map<String, Object> context = Map.of("userId", "123");

        List<RolloutStep> rollout = sdk.getVariableArray("typed", "rollout", context, RolloutStep.class);
        assertNotNull(rollout);
        assertEquals(2, rollout.size());
        assertEquals("phase-2", rollout.get(1).name);
        assertEquals(80, rollout.get(1).percentage);

        FeatureConfig config = sdk.getVariableObject("typed", "config", context, FeatureConfig.class);
        assertNotNull(config);
        assertEquals("strict", config.mode);
        assertEquals(50, config.threshold);

        List<Map<String, Object>> rolloutAsMaps = sdk.getVariableArray(
            "typed",
            "rollout",
            context,
            new TypeReference<List<Map<String, Object>>>() {}
        );
        assertEquals("phase-1", rolloutAsMaps.get(0).get("name"));

        // Safe fallback: invalid cast target should return null.
        Integer invalid = sdk.getVariableObject("typed", "config", context, Integer.class);
        assertNull(invalid);
    }
}
