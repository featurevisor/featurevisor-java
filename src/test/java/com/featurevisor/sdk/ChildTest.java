package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Segment;
import com.featurevisor.types.Variation;
import com.featurevisor.types.Traffic;
import com.featurevisor.types.Allocation;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.VariableOverride;
import com.featurevisor.types.Force;
import com.featurevisor.types.Condition;
import com.featurevisor.types.Bucket;
import com.featurevisor.types.Range;
import com.featurevisor.types.Operator;
import com.featurevisor.types.VariableType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChildTest {

    private Logger logger;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));
    }

    @Test
    public void testCreateChildInstance() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variablesSchema": {
                    "color": {
                      "key": "color",
                      "type": "string",
                      "defaultValue": "red"
                    },
                    "showSidebar": {
                      "key": "showSidebar",
                      "type": "boolean",
                      "defaultValue": false
                    },
                    "sidebarTitle": {
                      "key": "sidebarTitle",
                      "type": "string",
                      "defaultValue": "sidebar title"
                    },
                    "count": {
                      "key": "count",
                      "type": "integer",
                      "defaultValue": 0
                    },
                    "price": {
                      "key": "price",
                      "type": "double",
                      "defaultValue": 9.99
                    },
                    "paymentMethods": {
                      "key": "paymentMethods",
                      "type": "array",
                      "defaultValue": ["paypal", "creditcard"]
                    },
                    "flatConfig": {
                      "key": "flatConfig",
                      "type": "object",
                      "defaultValue": {
                        "key": "value"
                      }
                    },
                    "nestedConfig": {
                      "key": "nestedConfig",
                      "type": "json",
                      "defaultValue": {
                        "key": {
                          "nested": "value"
                        }
                      }
                    }
                  },
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment",
                      "variables": {
                        "showSidebar": true,
                        "sidebarTitle": "sidebar title from variation"
                      },
                      "variableOverrides": {
                        "showSidebar": [
                          {
                            "segments": ["netherlands"],
                            "value": false
                          },
                          {
                            "conditions": [
                              {
                                "attribute": "country",
                                "operator": "equals",
                                "value": "de"
                              }
                            ],
                            "value": false
                          }
                        ],
                        "sidebarTitle": [
                          {
                            "segments": ["netherlands"],
                            "value": "Dutch title"
                          },
                          {
                            "conditions": [
                              {
                                "attribute": "country",
                                "operator": "equals",
                                "value": "de"
                              }
                            ],
                            "value": "German title"
                          }
                        ]
                      }
                    }
                  ],
                  "force": [
                    {
                      "conditions": [
                        {
                          "attribute": "userId",
                          "operator": "equals",
                          "value": "user-ch"
                        }
                      ],
                      "enabled": true,
                      "variation": "control",
                      "variables": {
                        "color": "red and white"
                      }
                    },
                    {
                      "conditions": [
                        {
                          "attribute": "userId",
                          "operator": "equals",
                          "value": "user-gb"
                        }
                      ],
                      "enabled": false
                    },
                    {
                      "conditions": [
                        {
                          "attribute": "userId",
                          "operator": "equals",
                          "value": "user-forced-variation"
                        }
                      ],
                      "enabled": true,
                      "variation": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "2",
                      "segments": ["belgium"],
                      "percentage": 100000,
                      "variation": "control",
                      "variables": {
                        "color": "black"
                      },
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 0]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 100000]
                        }
                      ]
                    },
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 0]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 100000]
                        }
                      ]
                    }
                  ]
                },
                "anotherTest": {
                  "key": "anotherTest",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000
                    }
                  ]
                }
              },
              "segments": {
                "netherlands": {
                  "key": "netherlands",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"nl\\"}]"
                },
                "belgium": {
                  "key": "belgium",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"be\\"}]"
                }
              }
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create parent instance
        Map<String, Object> parentContext = new HashMap<>();
        parentContext.put("appVersion", "1.0.0");

        Featurevisor parentInstance = new Featurevisor(new Featurevisor.Options()
            .datafile(datafile)
            .context(parentContext));

        assertNotNull(parentInstance);
        assertEquals(parentContext, parentInstance.getContext());

        // Create child instance
        Map<String, Object> childContext = new HashMap<>();
        childContext.put("userId", "123");
        childContext.put("country", "nl");

        ChildInstance childInstance = parentInstance.spawn(childContext);

        assertNotNull(childInstance);

        // Check that child context includes parent context
        Map<String, Object> expectedChildContext = new HashMap<>();
        expectedChildContext.put("appVersion", "1.0.0");
        expectedChildContext.put("userId", "123");
        expectedChildContext.put("country", "nl");
        assertEquals(expectedChildContext, childInstance.getContext());

        // Test context updates
        childInstance.setContext(Map.of("country", "be"), false);

        Map<String, Object> updatedExpectedContext = new HashMap<>();
        updatedExpectedContext.put("appVersion", "1.0.0");
        updatedExpectedContext.put("userId", "123");
        updatedExpectedContext.put("country", "be");
        assertEquals(updatedExpectedContext, childInstance.getContext());

        // Test feature evaluation
        assertTrue(childInstance.isEnabled("test"));
        assertEquals("control", childInstance.getVariation("test"));

        // Test variable retrieval
        assertEquals("black", childInstance.getVariable("test", "color"));
        assertEquals("black", childInstance.getVariableString("test", "color"));

        assertEquals(false, childInstance.getVariable("test", "showSidebar"));
        assertEquals(false, childInstance.getVariableBoolean("test", "showSidebar"));

        assertEquals("sidebar title", childInstance.getVariable("test", "sidebarTitle"));
        assertEquals("sidebar title", childInstance.getVariableString("test", "sidebarTitle"));

        assertEquals(0, childInstance.getVariable("test", "count"));
        assertEquals(0, childInstance.getVariableInteger("test", "count"));

        assertEquals(9.99, childInstance.getVariable("test", "price"));
        assertEquals(9.99, childInstance.getVariableDouble("test", "price"));

        assertEquals(Arrays.asList("paypal", "creditcard"), childInstance.getVariable("test", "paymentMethods"));
        assertEquals(Arrays.asList("paypal", "creditcard"), childInstance.getVariableArray("test", "paymentMethods"));

        Map<String, Object> expectedFlatConfig = new HashMap<>();
        expectedFlatConfig.put("key", "value");
        assertEquals(expectedFlatConfig, childInstance.getVariable("test", "flatConfig"));
        assertEquals(expectedFlatConfig, childInstance.getVariableObject("test", "flatConfig"));

        Map<String, Object> expectedNestedConfig = new HashMap<>();
        Map<String, Object> nestedValue = new HashMap<>();
        nestedValue.put("nested", "value");
        expectedNestedConfig.put("key", nestedValue);
        assertEquals(expectedNestedConfig, childInstance.getVariable("test", "nestedConfig"));
        assertEquals(expectedNestedConfig, childInstance.getVariableJSON("test", "nestedConfig"));

        // Test sticky features
        assertFalse(childInstance.isEnabled("newFeature"));
        Map<String, Object> stickyFeature = new HashMap<>();
        stickyFeature.put("enabled", true);
        childInstance.setSticky(Map.of("newFeature", stickyFeature), false);
        assertTrue(childInstance.isEnabled("newFeature"));

        // Test getAllEvaluations
        com.featurevisor.types.EvaluatedFeatures allEvaluations = childInstance.getAllEvaluations();
        assertNotNull(allEvaluations.getValue());
        assertTrue(allEvaluations.getValue().containsKey("test"));
        assertTrue(allEvaluations.getValue().containsKey("anotherTest"));

        // Test close
        childInstance.close();
    }
}
