package com.featurevisor.sdk;

import com.featurevisor.sdk.DatafileContent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VariableOverrideParityTest {

    @Test
    public void testRuleVariableOverrideHasPriorityAndIndex() throws Exception {
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "test",
              "segments": {},
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "config": {
                      "type": "object",
                      "defaultValue": {"style": "default"}
                    }
                  },
                  "variations": [
                    {
                      "value": "control",
                      "variables": {
                        "config": {"style": "variation"}
                      }
                    }
                  ],
                  "traffic": [
                    {
                      "key": "rule-1",
                      "segments": "*",
                      "percentage": 100000,
                      "variation": "control",
                      "variables": {
                        "config": {"style": "rule-variable"}
                      },
                      "variableOverrides": {
                        "config": [
                          {
                            "conditions": [
                              {"attribute": "country", "operator": "equals", "value": "de"}
                            ],
                            "value": {"style": "rule-override"}
                          }
                        ]
                      },
                      "allocation": [
                        {"variation": "control", "range": [0, 100000]}
                      ]
                    }
                  ]
                }
              }
            }
            """;

        DatafileContent datafile = DatafileContent.fromJson(datafileJson);
        Featurevisor sdk = Featurevisor.createInstance(new Featurevisor.Options().datafile(datafile));

        Evaluation evaluation = sdk.evaluateVariable("test", "config", Map.of("userId", "123", "country", "de"));
        assertEquals(Evaluation.REASON_VARIABLE_OVERRIDE_RULE, evaluation.getReason());
        assertEquals(0, evaluation.getVariableOverrideIndex());

        @SuppressWarnings("unchecked")
        Map<String, Object> variableValue = (Map<String, Object>) evaluation.getVariableValue();
        assertNotNull(variableValue);
        assertEquals("rule-override", variableValue.get("style"));
    }

    @Test
    public void testVariationVariableOverrideHasReasonAndIndex() throws Exception {
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "test",
              "segments": {},
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "config": {
                      "type": "object",
                      "defaultValue": {"style": "default"}
                    }
                  },
                  "variations": [
                    {
                      "value": "control",
                      "variables": {
                        "config": {"style": "variation-variable"}
                      },
                      "variableOverrides": {
                        "config": [
                          {
                            "conditions": [
                              {"attribute": "country", "operator": "equals", "value": "de"}
                            ],
                            "value": {"style": "variation-override"}
                          }
                        ]
                      }
                    }
                  ],
                  "traffic": [
                    {
                      "key": "rule-1",
                      "segments": "*",
                      "percentage": 100000,
                      "variation": "control",
                      "allocation": [
                        {"variation": "control", "range": [0, 100000]}
                      ]
                    }
                  ]
                }
              }
            }
            """;

        DatafileContent datafile = DatafileContent.fromJson(datafileJson);
        Featurevisor sdk = Featurevisor.createInstance(new Featurevisor.Options().datafile(datafile));

        Evaluation evaluation = sdk.evaluateVariable("test", "config", Map.of("userId", "123", "country", "de"));
        assertEquals(Evaluation.REASON_VARIABLE_OVERRIDE_VARIATION, evaluation.getReason());
        assertEquals(0, evaluation.getVariableOverrideIndex());

        @SuppressWarnings("unchecked")
        Map<String, Object> variableValue = (Map<String, Object>) evaluation.getVariableValue();
        assertNotNull(variableValue);
        assertEquals("variation-override", variableValue.get("style"));
    }
}
