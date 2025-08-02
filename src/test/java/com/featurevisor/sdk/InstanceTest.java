package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Segment;
import com.featurevisor.types.Variation;
import com.featurevisor.types.Traffic;
import com.featurevisor.types.Allocation;
import com.featurevisor.types.VariableSchema;
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

public class InstanceTest {

    private Logger logger;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));
    }

    @Test
    public void testCreateInstanceIsFunction() {
        // This test verifies that Instance constructor exists
        assertNotNull(Instance.class.getConstructors());
    }

    @Test
    public void testCreateInstanceWithDatafileContent() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {},
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        // Test that the SDK was created successfully
        assertNotNull(sdk);
        // Test that getVariation returns null for non-existent feature (which is expected behavior)
        assertNull(sdk.getVariation("test"));
    }

    @Test
    public void testConfigurePlainBucketBy() {
        final String[] capturedBucketKey = {""};

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 0]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create hook to capture bucket key
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setBucketKey(options -> {
            capturedBucketKey[0] = options.getBucketKey();
            return options.getBucketKey();
        });

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        String featureKey = "test";
        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        assertTrue(sdk.isEnabled(featureKey, context));
        assertEquals("control", sdk.getVariation(featureKey, context));
        assertEquals("123.test", capturedBucketKey[0]);
    }

    @Test
    public void testConfigureAndBucketBy() {
        final String[] capturedBucketKey = {""};

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": ["userId", "organizationId"],
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 0]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create hook to capture bucket key
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setBucketKey(options -> {
            capturedBucketKey[0] = options.getBucketKey();
            return options.getBucketKey();
        });

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        String featureKey = "test";
        Map<String, Object> context = Map.of(
            "userId", "123",
            "organizationId", "456"
        );

        assertEquals("control", sdk.getVariation(featureKey, context));
        assertEquals("123.456.test", capturedBucketKey[0]);
    }

    @Test
    public void testConfigureOrBucketBy() {
        final String[] capturedBucketKey = {""};

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": {
                    "or": ["userId", "deviceId"]
                  },
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 0]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create hook to capture bucket key
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setBucketKey(options -> {
            capturedBucketKey[0] = options.getBucketKey();
            return options.getBucketKey();
        });

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        // Test with both userId and deviceId
        Map<String, Object> context1 = new HashMap<>();
        context1.put("userId", "123");
        context1.put("deviceId", "456");

        assertTrue(sdk.isEnabled("test", context1));
        assertEquals("control", sdk.getVariation("test", context1));
        assertEquals("123.test", capturedBucketKey[0]);

        // Test with only deviceId
        Map<String, Object> context2 = new HashMap<>();
        context2.put("deviceId", "456");

        assertEquals("control", sdk.getVariation("test", context2));
        assertEquals("456.test", capturedBucketKey[0]);
    }

    @Test
    public void testInterceptContextBeforeHook() {
        final boolean[] intercepted = {false};
        final String[] interceptedFeatureKey = {""};
        final String[] interceptedVariableKey = {""};

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [100000, 100000]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create hook to intercept before
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setBefore(options -> {
            intercepted[0] = true;
            interceptedFeatureKey[0] = options.getFeatureKey();
            interceptedVariableKey[0] = options.getVariableKey();
            return options;
        });

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        Object variation = sdk.getVariation("test", context);
        System.out.println("DEBUG: testInterceptContextBeforeHook variation = " + variation);

        // The hook should be called, but the variation might be null if the feature doesn't exist
        assertTrue(intercepted[0]);
        assertEquals("test", interceptedFeatureKey[0]);
        assertEquals(null, interceptedVariableKey[0]);
        // The variation should be "control" based on the allocation
        assertEquals("control", variation);
    }

    @Test
    public void testInterceptValueAfterHook() {
        final boolean[] intercepted = {false};
        final String[] interceptedFeatureKey = {""};
        final String[] interceptedVariableKey = {""};

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [100000, 100000]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create hook to intercept after and manipulate value
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setAfter((evaluation, options) -> {
            intercepted[0] = true;
            interceptedFeatureKey[0] = options.getFeatureKey();
            interceptedVariableKey[0] = options.getVariableKey();
            evaluation.setVariationValue("control_intercepted"); // manipulating value here
            return evaluation;
        });

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        Object variation = sdk.getVariation("test", context);
        System.out.println("DEBUG: testInterceptValueAfterHook variation = " + variation);

        // The hook should be called and the value should be intercepted
        assertTrue(intercepted[0]);
        assertEquals("test", interceptedFeatureKey[0]);
        assertEquals(null, interceptedVariableKey[0]);
        // The variation should be "control_intercepted" based on the after hook
        assertEquals("control_intercepted", variation);
    }

    @Test
    public void testInitializeWithStickyFeatures() throws InterruptedException {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
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
                }
              },
              "segments": {}
            }""";

        DatafileContent datafileContent;
        try {
            datafileContent = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        // Create sticky features
        Map<String, Object> sticky = new HashMap<>();
        Map<String, Object> testSticky = new HashMap<>();
        testSticky.put("enabled", true);
        testSticky.put("variation", "control");

        Map<String, Object> variables = new HashMap<>();
        variables.put("color", "red");
        testSticky.put("variables", variables);

        sticky.put("test", testSticky);

        Instance sdk = new Instance(new Instance.InstanceOptions().sticky(sticky));

        // initially control
        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        assertEquals("control", sdk.getVariation("test", context));
        assertEquals("red", sdk.getVariable("test", "color", context));

        sdk.setDatafile(datafileContent);

        Thread.sleep(75); // Wait for async operations

        // still control after setting datafile
        assertEquals("control", sdk.getVariation("test", context));

        // unsetting sticky features will make it treatment
        sdk.setSticky(new HashMap<>(), true);
        assertEquals("treatment", sdk.getVariation("test", context));
    }

    @Test
    public void testHonourSimpleRequiredFeatures() {
        // Test with required feature disabled
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "requiredKey": {
                  "key": "requiredKey",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 0,
                      "allocation": []
                    }
                  ]
                },
                "myKey": {
                  "key": "myKey",
                  "bucketBy": "userId",
                  "required": ["requiredKey"],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        // should be disabled because required is disabled
        assertFalse(sdk.isEnabled("myKey"));

        // Test with required feature enabled
        String datafileJsonEnabled = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "requiredKey": {
                  "key": "requiredKey",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                },
                "myKey": {
                  "key": "myKey",
                  "bucketBy": "userId",
                  "required": ["requiredKey"],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafileEnabled;
        try {
            datafileEnabled = DatafileContent.fromJson(datafileJsonEnabled);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk2 = new Instance(new Instance.InstanceOptions().datafile(datafileEnabled));
        assertTrue(sdk2.isEnabled("myKey"));
    }

    @Test
    public void testHonourRequiredFeaturesWithVariation() {
        // Test with required feature having different variation
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "requiredKey": {
                  "key": "requiredKey",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
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
                "myKey": {
                  "key": "myKey",
                  "bucketBy": "userId",
                  "required": [
                    {
                      "key": "requiredKey",
                      "variation": "control"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        assertFalse(sdk.isEnabled("myKey"));

        // Test with required feature having desired variation
        String datafileJsonDesired = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "requiredKey": {
                  "key": "requiredKey",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
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
                "myKey": {
                  "key": "myKey",
                  "bucketBy": "userId",
                  "required": [
                    {
                      "key": "requiredKey",
                      "variation": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafileDesired;
        try {
            datafileDesired = DatafileContent.fromJson(datafileJsonDesired);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk2 = new Instance(new Instance.InstanceOptions().datafile(datafileDesired));
        assertTrue(sdk2.isEnabled("myKey"));
    }

    @Test
    public void testEmitWarningsForDeprecatedFeature() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 0]
                        }
                      ]
                    }
                  ]
                },
                "deprecatedTest": {
                  "key": "deprecatedTest",
                  "deprecated": true,
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 100000]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 0]
                        }
                      ]
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        final int[] deprecatedCount = {0};

        Logger customLogger = Logger.createLogger(new Logger.CreateLoggerOptions()
            .handler((level, message, details) -> {
                if (level == Logger.LogLevel.WARN && message.contains("is deprecated")) {
                    deprecatedCount[0] += 1;
                }
            }));

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .logger(customLogger));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        Object testVariation = sdk.getVariation("test", context);
        Object deprecatedTestVariation = sdk.getVariation("deprecatedTest", context);

        assertEquals("control", testVariation);
        assertEquals("control", deprecatedTestVariation);
        assertEquals(1, deprecatedCount[0]);
    }

    @Test
    public void testGetVariation() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "traffic": [
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
                "testWithNoVariation": {
                  "key": "testWithNoVariation",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        assertEquals("treatment", sdk.getVariation("test", context));

        // non existing
        assertNull(sdk.getVariation("nonExistingFeature", context));

        // no variation
        assertNull(sdk.getVariation("testWithNoVariation", context));
    }

    @Test
    public void testGetVariable() {
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
                    }
                  },
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment",
                      "variables": {
                        "showSidebar": true
                      }
                    }
                  ],
                  "traffic": [
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
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        // Test variables
        assertEquals("red", sdk.getVariable("test", "color", context));
        assertEquals("red", sdk.getVariableString("test", "color", context));

        assertEquals(true, sdk.getVariable("test", "showSidebar", context));
        assertEquals(true, sdk.getVariableBoolean("test", "showSidebar", context));

        // non existing
        assertNull(sdk.getVariable("test", "nonExisting", context));
        assertNull(sdk.getVariable("nonExistingFeature", "nonExisting", context));
    }

    @Test
    public void testCheckIfEnabledForOverriddenFlagsFromRules() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "2",
                      "segments": ["netherlands"],
                      "percentage": 100000,
                      "enabled": false,
                      "allocation": []
                    },
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {
                "netherlands": {
                  "key": "netherlands",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"nl\\"}]"
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

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        // Test with German user (should be enabled)
        Map<String, Object> context1 = new HashMap<>();
        context1.put("userId", "user-123");
        context1.put("country", "de");
        assertTrue(sdk.isEnabled("test", context1));

        // Test with Dutch user (should be disabled)
        Map<String, Object> context2 = new HashMap<>();
        context2.put("userId", "user-123");
        context2.put("country", "nl");
        assertFalse(sdk.isEnabled("test", context2));
    }

    @Test
    public void testGetVariationWithForceRules() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "variations": [
                    {
                      "value": "control"
                    },
                    {
                      "value": "treatment"
                    }
                  ],
                  "force": [
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
                      "segments": ["netherlands"],
                      "enabled": false
                    }
                  ],
                  "traffic": [
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
                "testWithNoVariation": {
                  "key": "testWithNoVariation",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {
                "netherlands": {
                  "key": "netherlands",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"nl\\"}]"
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

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        assertEquals("treatment", sdk.getVariation("test", context));
        assertEquals("treatment", sdk.getVariation("test", Map.of(
            "userId", "user-ch"
        )));

        // non existing
        assertNull(sdk.getVariation("nonExistingFeature", context));

        // disabled by force rules
        assertNull(sdk.getVariation("test", Map.of(
            "userId", "user-gb"
        )));
        assertNull(sdk.getVariation("test", Map.of(
            "userId", "123",
            "country", "nl"
        )));

        // no variation
        assertNull(sdk.getVariation("testWithNoVariation", context));
    }

    @Test
    public void testCheckIfEnabledForMutuallyExclusiveFeatures() {
        final int[] bucketValue = {10000};

        // Create hook to control bucket value
        HooksManager.Hook hook = new HooksManager.Hook("unit-test");
        hook.setBucketValue(options -> bucketValue[0]);

        List<HooksManager.Hook> hooks = new ArrayList<>();
        hooks.add(hook);

        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "mutex": {
                  "key": "mutex",
                  "bucketBy": "userId",
                  "ranges": [
                    [0, 50000]
                  ],
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "*",
                      "percentage": 50000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {}
            }""";

        DatafileContent datafile;
        try {
            datafile = DatafileContent.fromJson(datafileJson);
        } catch (Exception e) {
            fail("Failed to parse datafile JSON: " + e.getMessage());
            return;
        }

        Instance sdk = new Instance(new Instance.InstanceOptions()
            .datafile(datafile)
            .hooks(hooks));

        // Test with no context (should be disabled)
        assertFalse(sdk.isEnabled("test"));
        assertFalse(sdk.isEnabled("test", new HashMap<>()));

        // Test with bucket value 40000 (should be enabled)
        bucketValue[0] = 40000;
        assertTrue(sdk.isEnabled("mutex", new HashMap<>()));

        // Test with bucket value 60000 (should be disabled)
        bucketValue[0] = 60000;
        assertFalse(sdk.isEnabled("mutex", new HashMap<>()));
    }

    @Test
    public void testGetVariableComprehensive() {
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
                      "allocation": [
                        {
                          "variation": "control",
                          "range": [0, 0]
                        },
                        {
                          "variation": "treatment",
                          "range": [0, 100000]
                        }
                      ],
                      "variation": "control",
                      "variables": {
                        "color": "black"
                      }
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

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        Map<String, Object> context = Map.of(
            "userId", "123"
        );

        // Test getAllEvaluations
        Map<String, Object> evaluatedFeatures = sdk.getAllEvaluations(context);
        assertNotNull(evaluatedFeatures);
        assertTrue(evaluatedFeatures.containsKey("test"));
        assertTrue(evaluatedFeatures.containsKey("anotherTest"));

        assertEquals("treatment", sdk.getVariation("test", context));
        assertEquals("control", sdk.getVariation("test", Map.of(
            "userId", "123",
            "country", "be"
        )));
        assertEquals("control", sdk.getVariation("test", Map.of(
            "userId", "user-ch"
        )));

        assertEquals("red", sdk.getVariable("test", "color", context));
        assertEquals("red", sdk.getVariableString("test", "color", context));
        assertEquals("black", sdk.getVariable("test", "color", Map.of(
            "userId", "123",
            "country", "be"
        )));
        assertEquals("red and white", sdk.getVariable("test", "color", Map.of(
            "userId", "user-ch"
        )));

        assertEquals(true, sdk.getVariable("test", "showSidebar", context));
        assertEquals(true, sdk.getVariableBoolean("test", "showSidebar", context));

        assertEquals("sidebar title from variation", sdk.getVariableString("test", "sidebarTitle", Map.of(
            "userId", "user-forced-variation"
        )));

        assertEquals(0, sdk.getVariable("test", "count", context));
        assertEquals(0, sdk.getVariableInteger("test", "count", context));

        assertEquals(9.99, sdk.getVariable("test", "price", context));
        assertEquals(9.99, sdk.getVariableDouble("test", "price", context));

        assertEquals(Arrays.asList("paypal", "creditcard"), sdk.getVariable("test", "paymentMethods", context));
        assertEquals(Arrays.asList("paypal", "creditcard"), sdk.getVariableArray("test", "paymentMethods", context));

        Map<String, Object> flatConfig = (Map<String, Object>) sdk.getVariable("test", "flatConfig", context);
        assertEquals("value", flatConfig.get("key"));
        assertEquals("value", ((Map<String, Object>) sdk.getVariableObject("test", "flatConfig", context)).get("key"));

        Map<String, Object> nestedConfig = (Map<String, Object>) sdk.getVariable("test", "nestedConfig", context);
        assertEquals("value", ((Map<String, Object>) nestedConfig.get("key")).get("nested"));
        assertEquals("value", ((Map<String, Object>) ((Map<String, Object>) sdk.getVariableJSON("test", "nestedConfig", context)).get("key")).get("nested"));

        // non existing
        assertNull(sdk.getVariable("test", "nonExisting", context));
        assertNull(sdk.getVariable("nonExistingFeature", "nonExisting", context));

        // disabled
        assertNull(sdk.getVariable("test", "color", Map.of(
            "userId", "user-gb"
        )));
    }

    @Test
    public void testGetVariablesWithoutAnyVariationsWithSegments() {
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
                    }
                  },
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "netherlands",
                      "percentage": 100000,
                      "variables": {
                        "color": "orange"
                      },
                      "allocation": []
                    },
                    {
                      "key": "2",
                      "segments": "*",
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {
                "netherlands": {
                  "key": "netherlands",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"nl\\"}]"
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

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        Map<String, Object> defaultContext = Map.of(
            "userId", "123"
        );

        // Test default value
        assertEquals("red", sdk.getVariable("test", "color", defaultContext));

        // Test override for netherlands
        assertEquals("orange", sdk.getVariable("test", "color", Map.of(
            "userId", "123",
            "country", "nl"
        )));
    }

    @Test
    public void testCheckIfEnabledForIndividuallyNamedSegments() {
        // Create datafile content using JSON string for better readability
        String datafileJson = """
            {
              "schemaVersion": "2",
              "revision": "1.0",
              "features": {
                "test": {
                  "key": "test",
                  "bucketBy": "userId",
                  "traffic": [
                    {
                      "key": "1",
                      "segments": "netherlands",
                      "percentage": 100000,
                      "allocation": []
                    },
                    {
                      "key": "2",
                      "segments": ["iphone", "unitedStates"],
                      "percentage": 100000,
                      "allocation": []
                    }
                  ]
                }
              },
              "segments": {
                "netherlands": {
                  "key": "netherlands",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"nl\\"}]"
                },
                "iphone": {
                  "key": "iphone",
                  "conditions": "[{\\"attribute\\":\\"device\\",\\"operator\\":\\"equals\\",\\"value\\":\\"iphone\\"}]"
                },
                "unitedStates": {
                  "key": "unitedStates",
                  "conditions": "[{\\"attribute\\":\\"country\\",\\"operator\\":\\"equals\\",\\"value\\":\\"us\\"}]"
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

        Instance sdk = new Instance(new Instance.InstanceOptions().datafile(datafile));

        // Test with no context (should be disabled)
        assertFalse(sdk.isEnabled("test"));
        assertFalse(sdk.isEnabled("test", new HashMap<>()));
        assertFalse(sdk.isEnabled("test", Map.of(
            "userId", "123"
        )));
        assertFalse(sdk.isEnabled("test", Map.of(
            "userId", "123",
            "country", "de"
        )));
        assertFalse(sdk.isEnabled("test", Map.of(
            "userId", "123",
            "country", "us"
        )));

        // Test with netherlands user (should be enabled)
        assertTrue(sdk.isEnabled("test", Map.of(
            "userId", "123",
            "country", "nl"
        )));

        // Test with US iPhone user (should be enabled)
        assertTrue(sdk.isEnabled("test", Map.of(
            "userId", "123",
            "country", "us",
            "device", "iphone"
        )));
    }
}
