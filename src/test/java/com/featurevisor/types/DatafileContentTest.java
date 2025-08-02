package com.featurevisor.types;

import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.VariableType;
import com.featurevisor.types.Force;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DatafileContentTest {

    @Test
    public void testParseJson() throws Exception {
        // Example JSON string that matches the DatafileContent structure
        String jsonString = "{\n" +
            "    \"schemaVersion\": \"1.0\",\n" +
            "    \"revision\": \"abc123\",\n" +
            "    \"segments\": {\n" +
            "        \"netherlands\": {\n" +
            "            \"key\": \"netherlands\",\n" +
            "            \"conditions\": {\n" +
            "                \"attribute\": \"country\",\n" +
            "                \"operator\": \"equals\",\n" +
            "                \"value\": \"NL\"\n" +
            "            }\n" +
            "        }\n" +
            "    },\n" +
            "    \"features\": {\n" +
            "        \"showCookieBanner\": {\n" +
            "            \"key\": \"showCookieBanner\",\n" +
            "            \"bucketBy\": \"deviceId\",\n" +
            "            \"traffic\": [\n" +
            "                {\n" +
            "                    \"key\": \"1\",\n" +
            "                    \"segments\": \"*\",\n" +
            "                    \"percentage\": 100000\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    }\n" +
            "}";

        // Parse the JSON string
        DatafileContent datafile = DatafileContent.fromJson(jsonString);

        // Verify the parsed data
        assertEquals("1.0", datafile.getSchemaVersion());
        assertEquals("abc123", datafile.getRevision());
        assertEquals(1, datafile.getSegmentCount());
        assertEquals(1, datafile.getFeatureCount());

        // Test getting specific features and segments
        assertTrue(datafile.hasFeature("showCookieBanner"));
        assertTrue(datafile.hasSegment("netherlands"));

        Feature feature = datafile.getFeature("showCookieBanner");
        assertNotNull(feature);
        assertEquals("showCookieBanner", feature.getKey());

        Segment segment = datafile.getSegment("netherlands");
        assertNotNull(segment);
        assertEquals("netherlands", segment.getKey());
    }

    @Test
    public void testToJson() throws Exception {
        // Create a DatafileContent object
        DatafileContent datafile = new DatafileContent("1.0", "test123");

        // Convert to JSON
        String json = datafile.toJson();

        // Parse it back
        DatafileContent parsed = DatafileContent.fromJson(json);

        // Verify it's the same
        assertEquals(datafile.getSchemaVersion(), parsed.getSchemaVersion());
        assertEquals(datafile.getRevision(), parsed.getRevision());
    }

    @Test
    public void testComplexFeature() throws Exception {
        String jsonString = "{\n" +
            "    \"schemaVersion\": \"1.0\",\n" +
            "    \"revision\": \"complex123\",\n" +
            "    \"segments\": {},\n" +
            "    \"features\": {\n" +
            "        \"complexFeature\": {\n" +
            "            \"key\": \"complexFeature\",\n" +
            "            \"hash\": \"hash123\",\n" +
            "            \"deprecated\": false,\n" +
            "            \"bucketBy\": {\n" +
            "                \"or\": [\"userId\", \"deviceId\"]\n" +
            "            },\n" +
            "            \"variations\": [\n" +
            "                {\n" +
            "                    \"value\": \"on\",\n" +
            "                    \"weight\": 50,\n" +
            "                    \"variables\": {\n" +
            "                        \"message\": \"Hello World\"\n" +
            "                    }\n" +
            "                },\n" +
            "                {\n" +
            "                    \"value\": \"off\",\n" +
            "                    \"weight\": 50\n" +
            "                }\n" +
            "            ],\n" +
            "            \"traffic\": [\n" +
            "                {\n" +
            "                    \"key\": \"1\",\n" +
            "                    \"segments\": \"*\",\n" +
            "                    \"percentage\": 100000,\n" +
            "                    \"allocation\": [\n" +
            "                        {\n" +
            "                            \"variation\": \"on\",\n" +
            "                            \"range\": [0, 50000]\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"variation\": \"off\",\n" +
            "                            \"range\": [50000, 100000]\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    }\n" +
            "}";

        DatafileContent datafile = DatafileContent.fromJson(jsonString);
        Feature feature = datafile.getFeature("complexFeature");

        assertNotNull(feature);
        assertEquals("complexFeature", feature.getKey());
        assertEquals("hash123", feature.getHash());
        assertFalse(feature.getDeprecated());

        // Test bucketBy
        assertNotNull(feature.getBucketBy());
        assertTrue(feature.getBucketBy().isOrBucketBy());

        // Test variations
        assertNotNull(feature.getVariations());
        assertEquals(2, feature.getVariations().size());

        // Test traffic
        assertNotNull(feature.getTraffic());
        assertEquals(1, feature.getTraffic().size());

        Traffic traffic = feature.getTraffic().get(0);
        assertEquals("1", traffic.getKey());
        assertEquals(100000, traffic.getPercentage());

        // Test allocation
        assertNotNull(traffic.getAllocation());
        assertEquals(2, traffic.getAllocation().size());
    }

    @Test
    public void testStagingDatafile() throws Exception {
        String jsonString = "{\n" +
            "  \"schemaVersion\": \"2\",\n" +
            "  \"revision\": \"335\",\n" +
            "  \"segments\": {\n" +
            "    \"countries/germany\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"de\\\"}]}\"\n" +
            "    },\n" +
            "    \"countries/netherlands\": {\n" +
            "      \"conditions\": \"[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"nl\\\"}]\"\n" +
            "    },\n" +
            "    \"countries/switzerland\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"ch\\\"}]}\"\n" +
            "    },\n" +
            "    \"qa\": {\n" +
            "      \"conditions\": \"[{\\\"attribute\\\":\\\"userId\\\",\\\"operator\\\":\\\"in\\\",\\\"value\\\":[\\\"user-1\\\",\\\"user-2\\\"]}]\"\n" +
            "    },\n" +
            "    \"version_5.5\": {\n" +
            "      \"conditions\": \"[{\\\"or\\\":[{\\\"attribute\\\":\\\"version\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":5.5},{\\\"attribute\\\":\\\"version\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"5.5\\\"}]}]\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"features\": {\n" +
            "    \"allowSignup\": {\n" +
            "      \"bucketBy\": \"deviceId\",\n" +
            "      \"variations\": [\n" +
            "        {\n" +
            "          \"value\": \"control\",\n" +
            "          \"weight\": 50\n" +
            "        },\n" +
            "        {\n" +
            "          \"value\": \"treatment\",\n" +
            "          \"weight\": 50,\n" +
            "          \"variables\": {\n" +
            "            \"allowGoogleSignUp\": true,\n" +
            "            \"allowGitHubSignUp\": true\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"1\",\n" +
            "          \"segments\": \"*\",\n" +
            "          \"percentage\": 100000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                50000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                50000,\n" +
            "                100000\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ],\n" +
            "      \"variablesSchema\": {\n" +
            "        \"allowRegularSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": true\n" +
            "        },\n" +
            "        \"allowGoogleSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": false\n" +
            "        },\n" +
            "        \"allowGitHubSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": false\n" +
            "        }\n" +
            "      },\n" +
            "      \"hash\": \"zX8bZtkm5V\"\n" +
            "    },\n" +
            "    \"bar\": {\n" +
            "      \"bucketBy\": \"userId\",\n" +
            "      \"variations\": [\n" +
            "        {\n" +
            "          \"value\": \"control\",\n" +
            "          \"weight\": 33\n" +
            "        },\n" +
            "        {\n" +
            "          \"value\": \"b\",\n" +
            "          \"weight\": 33,\n" +
            "          \"variables\": {\n" +
            "            \"hero\": {\n" +
            "              \"title\": \"Hero Title for B\",\n" +
            "              \"subtitle\": \"Hero Subtitle for B\",\n" +
            "              \"alignment\": \"center for B\"\n" +
            "            }\n" +
            "          },\n" +
            "          \"variableOverrides\": {\n" +
            "            \"hero\": [\n" +
            "              {\n" +
            "                \"segments\": \"{\\\"or\\\":[\\\"countries/germany\\\",\\\"countries/switzerland\\\"]}\",\n" +
            "                \"value\": {\n" +
            "                  \"title\": \"Hero Title for B in DE or CH\",\n" +
            "                  \"subtitle\": \"Hero Subtitle for B in DE of CH\",\n" +
            "                  \"alignment\": \"center for B in DE or CH\"\n" +
            "                }\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"value\": \"c\",\n" +
            "          \"weight\": 34\n" +
            "        }\n" +
            "      ],\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"1\",\n" +
            "          \"segments\": \"*\",\n" +
            "          \"percentage\": 50000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                16500\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"b\",\n" +
            "              \"range\": [\n" +
            "                16500,\n" +
            "                33000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"c\",\n" +
            "              \"range\": [\n" +
            "                33000,\n" +
            "                50000\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ],\n" +
            "      \"ranges\": [\n" +
            "        [\n" +
            "          0,\n" +
            "          50000\n" +
            "        ]\n" +
            "      ],\n" +
            "      \"variablesSchema\": {\n" +
            "        \"color\": {\n" +
            "          \"type\": \"string\",\n" +
            "          \"defaultValue\": \"red\"\n" +
            "        },\n" +
            "        \"hero\": {\n" +
            "          \"type\": \"object\",\n" +
            "          \"defaultValue\": {\n" +
            "            \"title\": \"Hero Title\",\n" +
            "            \"subtitle\": \"Hero Subtitle\",\n" +
            "            \"alignment\": \"center\"\n" +
            "          }\n" +
            "        }\n" +
            "      },\n" +
            "      \"hash\": \"2bGkQN1GnW\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        // Parse the JSON string
        DatafileContent datafile = DatafileContent.fromJson(jsonString);

        // Verify basic structure
        assertEquals("2", datafile.getSchemaVersion());
        assertEquals("335", datafile.getRevision());
        assertEquals(5, datafile.getSegmentCount());
        assertEquals(2, datafile.getFeatureCount());

        // Test segments
        assertTrue(datafile.hasSegment("countries/germany"));
        assertTrue(datafile.hasSegment("countries/netherlands"));
        assertTrue(datafile.hasSegment("countries/switzerland"));
        assertTrue(datafile.hasSegment("qa"));
        assertTrue(datafile.hasSegment("version_5.5"));

        // Test features
        assertTrue(datafile.hasFeature("allowSignup"));
        assertTrue(datafile.hasFeature("bar"));

        // Test allowSignup feature
        Feature allowSignup = datafile.getFeature("allowSignup");
        assertNotNull(allowSignup);
        assertEquals("deviceId", allowSignup.getBucketBy().getPlainBucketBy());
        assertEquals("zX8bZtkm5V", allowSignup.getHash());

        // Test variations
        assertNotNull(allowSignup.getVariations());
        assertEquals(2, allowSignup.getVariations().size());

        Variation controlVariation = allowSignup.getVariations().get(0);
        assertEquals("control", controlVariation.getValue());
        assertEquals(50, controlVariation.getWeight());

        Variation treatmentVariation = allowSignup.getVariations().get(1);
        assertEquals("treatment", treatmentVariation.getValue());
        assertEquals(50, treatmentVariation.getWeight());
        assertNotNull(treatmentVariation.getVariables());
        assertTrue((Boolean) treatmentVariation.getVariables().get("allowGoogleSignUp"));
        assertTrue((Boolean) treatmentVariation.getVariables().get("allowGitHubSignUp"));

        // Test traffic
        assertNotNull(allowSignup.getTraffic());
        assertEquals(1, allowSignup.getTraffic().size());

        Traffic traffic = allowSignup.getTraffic().get(0);
        assertEquals("1", traffic.getKey());
        assertEquals("*", traffic.getSegments());
        assertEquals(100000, traffic.getPercentage());

        // Test allocation
        assertNotNull(traffic.getAllocation());
        assertEquals(2, traffic.getAllocation().size());

        Allocation controlAllocation = traffic.getAllocation().get(0);
        assertEquals("control", controlAllocation.getVariation());
        assertEquals(0, controlAllocation.getRange().getStart());
        assertEquals(50000, controlAllocation.getRange().getEnd());

        // Test variablesSchema
        assertNotNull(allowSignup.getVariablesSchema());
        assertEquals(3, allowSignup.getVariablesSchema().size());

        VariableSchema allowRegularSignUpSchema = allowSignup.getVariablesSchema().get("allowRegularSignUp");
        assertEquals(VariableType.BOOLEAN, allowRegularSignUpSchema.getType());
        assertTrue((Boolean) allowRegularSignUpSchema.getDefaultValue());

        // Test bar feature with complex structure
        Feature bar = datafile.getFeature("bar");
        assertNotNull(bar);
        assertEquals("userId", bar.getBucketBy().getPlainBucketBy());
        assertEquals("2bGkQN1GnW", bar.getHash());

        // Test variations with variableOverrides
        assertNotNull(bar.getVariations());
        assertEquals(3, bar.getVariations().size());

        Variation barVariation = bar.getVariations().get(1); // "b" variation
        assertEquals("b", barVariation.getValue());
        assertEquals(33, barVariation.getWeight());

        // Test variables in variation
        assertNotNull(barVariation.getVariables());
        Object heroVariable = barVariation.getVariables().get("hero");
        assertNotNull(heroVariable);

        // Test variableOverrides
        assertNotNull(barVariation.getVariableOverrides());
        assertTrue(barVariation.getVariableOverrides().containsKey("hero"));

        // Test ranges
        assertNotNull(bar.getRanges());
        assertEquals(1, bar.getRanges().size());
        assertEquals(0, bar.getRanges().get(0).getStart());
        assertEquals(50000, bar.getRanges().get(0).getEnd());
    }

    @Test
    public void testProductionDatafile() throws Exception {
        String jsonString = "{\n" +
            "  \"schemaVersion\": \"2\",\n" +
            "  \"revision\": \"335\",\n" +
            "  \"segments\": {\n" +
            "    \"blackFridayWeekend\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"date\\\",\\\"operator\\\":\\\"after\\\",\\\"value\\\":\\\"2023-11-24T00:00:00.000Z\\\"},{\\\"attribute\\\":\\\"date\\\",\\\"operator\\\":\\\"before\\\",\\\"value\\\":\\\"2023-11-27T00:00:00.000Z\\\"}]}\"\n" +
            "    },\n" +
            "    \"countries/germany\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"de\\\"}]}\"\n" +
            "    },\n" +
            "    \"countries/netherlands\": {\n" +
            "      \"conditions\": \"[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"nl\\\"}]\"\n" +
            "    },\n" +
            "    \"countries/switzerland\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"ch\\\"}]}\"\n" +
            "    },\n" +
            "    \"desktop\": {\n" +
            "      \"conditions\": \"[{\\\"attribute\\\":\\\"device\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"desktop\\\"}]\"\n" +
            "    },\n" +
            "    \"everyone\": {\n" +
            "      \"conditions\": \"*\"\n" +
            "    },\n" +
            "    \"mobile\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"device\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"mobile\\\"},{\\\"attribute\\\":\\\"phone\\\",\\\"operator\\\":\\\"notExists\\\"}]}\"\n" +
            "    },\n" +
            "    \"version_gt5\": {\n" +
            "      \"conditions\": \"[{\\\"attribute\\\":\\\"version\\\",\\\"operator\\\":\\\"semverGreaterThan\\\",\\\"value\\\":\\\"5.0.0\\\"}]\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"features\": {\n" +
            "    \"allowSignup\": {\n" +
            "      \"bucketBy\": \"deviceId\",\n" +
            "      \"variations\": [\n" +
            "        {\n" +
            "          \"value\": \"control\",\n" +
            "          \"weight\": 50\n" +
            "        },\n" +
            "        {\n" +
            "          \"value\": \"treatment\",\n" +
            "          \"weight\": 50,\n" +
            "          \"variables\": {\n" +
            "            \"allowGoogleSignUp\": true,\n" +
            "            \"allowGitHubSignUp\": true\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"nl\",\n" +
            "          \"segments\": \"[\\\"countries/netherlands\\\"]\",\n" +
            "          \"percentage\": 100000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                50000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                50000,\n" +
            "                100000\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"variation\": \"treatment\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"ch\",\n" +
            "          \"segments\": \"[\\\"countries/switzerland\\\"]\",\n" +
            "          \"percentage\": 100000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                10000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                10000,\n" +
            "                100000\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"variationWeights\": {\n" +
            "            \"control\": 10,\n" +
            "            \"treatment\": 90\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"everyone\",\n" +
            "          \"segments\": \"everyone\",\n" +
            "          \"percentage\": 100000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                50000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                50000,\n" +
            "                100000\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ],\n" +
            "      \"variablesSchema\": {\n" +
            "        \"allowRegularSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": true\n" +
            "        },\n" +
            "        \"allowGoogleSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": false\n" +
            "        },\n" +
            "        \"allowGitHubSignUp\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": false\n" +
            "        }\n" +
            "      },\n" +
            "      \"hash\": \"8ZwSp88Vqf\"\n" +
            "    },\n" +
            "    \"foo\": {\n" +
            "      \"bucketBy\": \"userId\",\n" +
            "      \"variations\": [\n" +
            "        {\n" +
            "          \"value\": \"control\",\n" +
            "          \"weight\": 50\n" +
            "        },\n" +
            "        {\n" +
            "          \"value\": \"treatment\",\n" +
            "          \"weight\": 50,\n" +
            "          \"variables\": {\n" +
            "            \"bar\": \"bar_here\",\n" +
            "            \"baz\": \"baz_here\"\n" +
            "          },\n" +
            "          \"variableOverrides\": {\n" +
            "            \"bar\": [\n" +
            "              {\n" +
            "                \"segments\": \"{\\\"or\\\":[\\\"countries/germany\\\",\\\"countries/switzerland\\\"]}\",\n" +
            "                \"value\": \"bar for DE or CH\"\n" +
            "              }\n" +
            "            ],\n" +
            "            \"baz\": [\n" +
            "              {\n" +
            "                \"segments\": \"countries/netherlands\",\n" +
            "                \"value\": \"baz for NL\"\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"1\",\n" +
            "          \"segments\": \"{\\\"and\\\":[\\\"mobile\\\",{\\\"or\\\":[\\\"countries/germany\\\",\\\"countries/switzerland\\\"]}]}\",\n" +
            "          \"percentage\": 80000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                40000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                40000,\n" +
            "                80000\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"variables\": {\n" +
            "            \"qux\": true\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"2\",\n" +
            "          \"segments\": \"*\",\n" +
            "          \"percentage\": 50000,\n" +
            "          \"allocation\": [\n" +
            "            {\n" +
            "              \"variation\": \"control\",\n" +
            "              \"range\": [\n" +
            "                0,\n" +
            "                25000\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"variation\": \"treatment\",\n" +
            "              \"range\": [\n" +
            "                25000,\n" +
            "                50000\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ],\n" +
            "      \"variablesSchema\": {\n" +
            "        \"bar\": {\n" +
            "          \"type\": \"string\",\n" +
            "          \"defaultValue\": \"\"\n" +
            "        },\n" +
            "        \"baz\": {\n" +
            "          \"type\": \"string\",\n" +
            "          \"defaultValue\": \"\"\n" +
            "        },\n" +
            "        \"qux\": {\n" +
            "          \"type\": \"boolean\",\n" +
            "          \"defaultValue\": false\n" +
            "        }\n" +
            "      },\n" +
            "      \"force\": [\n" +
            "        {\n" +
            "          \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"userId\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"123\\\"},{\\\"attribute\\\":\\\"device\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"mobile\\\"}]}\",\n" +
            "          \"variation\": \"treatment\",\n" +
            "          \"variables\": {\n" +
            "            \"bar\": \"yoooooo\"\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"hash\": \"sjCzQ7BZZa\"\n" +
            "    },\n" +
            "    \"showHeader\": {\n" +
            "      \"bucketBy\": [\n" +
            "        \"userId\"\n" +
            "      ],\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"desktop\",\n" +
            "          \"segments\": \"[\\\"version_gt5\\\",\\\"desktop\\\"]\",\n" +
            "          \"percentage\": 100000\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"mobile\",\n" +
            "          \"segments\": \"[\\\"mobile\\\"]\",\n" +
            "          \"percentage\": 100000\n" +
            "        },\n" +
            "        {\n" +
            "          \"key\": \"all\",\n" +
            "          \"segments\": \"*\",\n" +
            "          \"percentage\": 0\n" +
            "        }\n" +
            "      ],\n" +
            "      \"hash\": \"ef6wRyFiNw\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        // Parse the JSON string
        DatafileContent datafile = DatafileContent.fromJson(jsonString);

        // Verify basic structure
        assertEquals("2", datafile.getSchemaVersion());
        assertEquals("335", datafile.getRevision());
        assertEquals(8, datafile.getSegmentCount());
        assertEquals(3, datafile.getFeatureCount());

        // Test segments
        assertTrue(datafile.hasSegment("blackFridayWeekend"));
        assertTrue(datafile.hasSegment("countries/germany"));
        assertTrue(datafile.hasSegment("countries/netherlands"));
        assertTrue(datafile.hasSegment("countries/switzerland"));
        assertTrue(datafile.hasSegment("desktop"));
        assertTrue(datafile.hasSegment("everyone"));
        assertTrue(datafile.hasSegment("mobile"));
        assertTrue(datafile.hasSegment("version_gt5"));

        // Test features
        assertTrue(datafile.hasFeature("allowSignup"));
        assertTrue(datafile.hasFeature("foo"));
        assertTrue(datafile.hasFeature("showHeader"));

        // Test allowSignup feature with multiple traffic rules
        Feature allowSignup = datafile.getFeature("allowSignup");
        assertNotNull(allowSignup);
        assertEquals("deviceId", allowSignup.getBucketBy().getPlainBucketBy());
        assertEquals("8ZwSp88Vqf", allowSignup.getHash());

        // Test traffic with multiple rules
        assertNotNull(allowSignup.getTraffic());
        assertEquals(3, allowSignup.getTraffic().size());

        // Test first traffic rule (nl)
        Traffic nlTraffic = allowSignup.getTraffic().get(0);
        assertEquals("nl", nlTraffic.getKey());
        assertEquals("[\"countries/netherlands\"]", nlTraffic.getSegments());
        assertEquals(100000, nlTraffic.getPercentage());
        assertEquals("treatment", nlTraffic.getVariation());

        // Test second traffic rule (ch) with variationWeights
        Traffic chTraffic = allowSignup.getTraffic().get(1);
        assertEquals("ch", chTraffic.getKey());
        assertEquals("[\"countries/switzerland\"]", chTraffic.getSegments());
        assertEquals(100000, chTraffic.getPercentage());
        assertNotNull(chTraffic.getVariationWeights());
        assertEquals(10, chTraffic.getVariationWeights().get("control"));
        assertEquals(90, chTraffic.getVariationWeights().get("treatment"));

        // Test third traffic rule (everyone)
        Traffic everyoneTraffic = allowSignup.getTraffic().get(2);
        assertEquals("everyone", everyoneTraffic.getKey());
        assertEquals("everyone", everyoneTraffic.getSegments());
        assertEquals(100000, everyoneTraffic.getPercentage());

        // Test foo feature with complex traffic and force rules
        Feature foo = datafile.getFeature("foo");
        assertNotNull(foo);
        assertEquals("userId", foo.getBucketBy().getPlainBucketBy());
        assertEquals("sjCzQ7BZZa", foo.getHash());

        // Test variations with variableOverrides
        assertNotNull(foo.getVariations());
        assertEquals(2, foo.getVariations().size());

        Variation treatmentVariation = foo.getVariations().get(1);
        assertEquals("treatment", treatmentVariation.getValue());
        assertEquals(50, treatmentVariation.getWeight());

        // Test variableOverrides
        assertNotNull(treatmentVariation.getVariableOverrides());
        assertTrue(treatmentVariation.getVariableOverrides().containsKey("bar"));
        assertTrue(treatmentVariation.getVariableOverrides().containsKey("baz"));

        // Test traffic with complex segments
        assertNotNull(foo.getTraffic());
        assertEquals(2, foo.getTraffic().size());

        Traffic complexTraffic = foo.getTraffic().get(0);
        assertEquals("1", complexTraffic.getKey());
        assertEquals("{\"and\":[\"mobile\",{\"or\":[\"countries/germany\",\"countries/switzerland\"]}]}", complexTraffic.getSegments());
        assertEquals(80000, complexTraffic.getPercentage());

        // Test variables in traffic
        assertNotNull(complexTraffic.getVariables());
        assertTrue((Boolean) complexTraffic.getVariables().get("qux"));

        // Test force rules
        assertNotNull(foo.getForce());
        assertEquals(1, foo.getForce().size());

        Force forceRule = foo.getForce().get(0);
        assertEquals("{\"and\":[{\"attribute\":\"userId\",\"operator\":\"equals\",\"value\":\"123\"},{\"attribute\":\"device\",\"operator\":\"equals\",\"value\":\"mobile\"}]}", forceRule.getConditions());
        assertEquals("treatment", forceRule.getVariation());
        assertNotNull(forceRule.getVariables());
        assertEquals("yoooooo", forceRule.getVariables().get("bar"));

        // Test showHeader feature with array bucketBy
        Feature showHeader = datafile.getFeature("showHeader");
        assertNotNull(showHeader);
        assertTrue(showHeader.getBucketBy().isAndBucketBy());
        assertEquals("ef6wRyFiNw", showHeader.getHash());

        // Test traffic with multiple rules
        assertNotNull(showHeader.getTraffic());
        assertEquals(3, showHeader.getTraffic().size());

        Traffic desktopTraffic = showHeader.getTraffic().get(0);
        assertEquals("desktop", desktopTraffic.getKey());
        assertEquals("[\"version_gt5\",\"desktop\"]", desktopTraffic.getSegments());
        assertEquals(100000, desktopTraffic.getPercentage());

        Traffic mobileTraffic = showHeader.getTraffic().get(1);
        assertEquals("mobile", mobileTraffic.getKey());
        assertEquals("[\"mobile\"]", mobileTraffic.getSegments());
        assertEquals(100000, mobileTraffic.getPercentage());

        Traffic allTraffic = showHeader.getTraffic().get(2);
        assertEquals("all", allTraffic.getKey());
        assertEquals("*", allTraffic.getSegments());
        assertEquals(0, allTraffic.getPercentage());
    }

    @Test
    public void testComplexSegments() throws Exception {
        // Test parsing of complex segment conditions
        String jsonString = "{\n" +
            "  \"schemaVersion\": \"2\",\n" +
            "  \"revision\": \"1\",\n" +
            "  \"segments\": {\n" +
            "    \"complex_segment\": {\n" +
            "      \"conditions\": \"{\\\"and\\\":[{\\\"attribute\\\":\\\"country\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"us\\\"},{\\\"or\\\":[{\\\"attribute\\\":\\\"device\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"mobile\\\"},{\\\"attribute\\\":\\\"device\\\",\\\"operator\\\":\\\"equals\\\",\\\"value\\\":\\\"tablet\\\"}]}]}\"\n" +
            "    },\n" +
            "    \"simple_segment\": {\n" +
            "      \"conditions\": \"*\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"features\": {}\n" +
            "}";

        DatafileContent datafile = DatafileContent.fromJson(jsonString);

        assertEquals(2, datafile.getSegmentCount());
        assertTrue(datafile.hasSegment("complex_segment"));
        assertTrue(datafile.hasSegment("simple_segment"));

        Segment complexSegment = datafile.getSegment("complex_segment");
        assertNotNull(complexSegment);
        assertNotNull(complexSegment.getConditions());

        Segment simpleSegment = datafile.getSegment("simple_segment");
        assertNotNull(simpleSegment);
        assertEquals("*", simpleSegment.getConditions());
    }

    @Test
    public void testEdgeCases() throws Exception {
        // Test edge cases like empty features, empty segments, etc.
        String jsonString = "{\n" +
            "  \"schemaVersion\": \"2\",\n" +
            "  \"revision\": \"1\",\n" +
            "  \"segments\": {},\n" +
            "  \"features\": {\n" +
            "    \"empty_feature\": {\n" +
            "      \"bucketBy\": \"userId\",\n" +
            "      \"traffic\": [\n" +
            "        {\n" +
            "          \"key\": \"1\",\n" +
            "          \"segments\": \"*\",\n" +
            "          \"percentage\": 0\n" +
            "        }\n" +
            "      ],\n" +
            "      \"hash\": \"empty123\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        DatafileContent datafile = DatafileContent.fromJson(jsonString);

        assertEquals(0, datafile.getSegmentCount());
        assertEquals(1, datafile.getFeatureCount());

        Feature emptyFeature = datafile.getFeature("empty_feature");
        assertNotNull(emptyFeature);
        assertEquals("empty123", emptyFeature.getHash());
        assertNull(emptyFeature.getVariations());
        assertNull(emptyFeature.getVariablesSchema());
        assertNull(emptyFeature.getForce());
        assertNull(emptyFeature.getRanges());
    }
}
