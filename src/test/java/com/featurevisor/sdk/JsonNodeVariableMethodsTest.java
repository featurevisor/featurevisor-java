package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.featurevisor.types.DatafileContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonNodeVariableMethodsTest {

    private static final String DATAFILE_JSON = """
        {
          "schemaVersion": "2",
          "revision": "json-node-test",
          "segments": {},
          "features": {
            "json_feature": {
              "key": "json_feature",
              "bucketBy": "userId",
              "variablesSchema": {
                "jsonObject": {
                  "type": "json",
                  "defaultValue": {}
                },
                "jsonArray": {
                  "type": "json",
                  "defaultValue": []
                },
                "malformedJson": {
                  "type": "json",
                  "defaultValue": {}
                },
                "regularString": {
                  "type": "string",
                  "defaultValue": "default-value"
                }
              },
              "traffic": [
                {
                  "key": "rule-1",
                  "segments": "*",
                  "percentage": 100000,
                  "variables": {
                    "jsonObject": "{\\"a\\":1,\\"nested\\":{\\"b\\":\\"x\\"}}",
                    "jsonArray": "[{\\"id\\":1},2,\\"x\\"]",
                    "malformedJson": "{ invalid json",
                    "regularString": "plain-value"
                  }
                }
              ]
            }
          }
        }
        """;

    @Test
    public void testJsonNodeMethodsAndMalformedJsonHandling() throws Exception {
        Featurevisor sdk = Featurevisor.createInstance(
            new Featurevisor.Options().datafile(DatafileContent.fromJson(DATAFILE_JSON))
        );
        Map<String, Object> context = Map.of("userId", "123");

        JsonNode objectNode = sdk.getVariableJSONNode("json_feature", "jsonObject", context);
        assertNotNull(objectNode);
        assertTrue(objectNode.isObject());
        assertEquals(1, objectNode.get("a").asInt());
        assertEquals("x", objectNode.get("nested").get("b").asText());

        JsonNode arrayNode = sdk.getVariableJSONNode("json_feature", "jsonArray", context);
        assertNotNull(arrayNode);
        assertTrue(arrayNode.isArray());
        assertEquals(1, arrayNode.get(0).get("id").asInt());
        assertEquals(2, arrayNode.get(1).asInt());
        assertEquals("x", arrayNode.get(2).asText());

        Map<String, Object> jsonAsMap = sdk.getVariableJSON("json_feature", "jsonObject", context);
        assertNotNull(jsonAsMap);
        assertEquals(1, ((Number) jsonAsMap.get("a")).intValue());
        assertEquals("x", ((Map<String, Object>) jsonAsMap.get("nested")).get("b"));

        assertNull(sdk.getVariable("json_feature", "malformedJson", context));
        assertNull(sdk.getVariableJSONNode("json_feature", "malformedJson", context));
        assertNull(sdk.getVariableJSON("json_feature", "malformedJson", context));

        assertEquals("plain-value", sdk.getVariable("json_feature", "regularString", context));
        assertEquals("plain-value", sdk.getVariableString("json_feature", "regularString", context));
    }

    @Test
    public void testChildInstanceJsonNodeParity() throws Exception {
        Featurevisor sdk = Featurevisor.createInstance(
            new Featurevisor.Options().datafile(DatafileContent.fromJson(DATAFILE_JSON))
        );
        ChildInstance child = sdk.spawn(Map.of("userId", "123"));

        JsonNode objectNode = child.getVariableJSONNode("json_feature", "jsonObject");
        assertNotNull(objectNode);
        assertEquals("x", objectNode.get("nested").get("b").asText());

        JsonNode arrayNode = child.getVariableJSONNode("json_feature", "jsonArray");
        assertNotNull(arrayNode);
        assertEquals(3, arrayNode.size());

        assertNull(child.getVariable("json_feature", "malformedJson"));
        assertNull(child.getVariableJSONNode("json_feature", "malformedJson"));
        assertNull(child.getVariableJSON("json_feature", "malformedJson"));

        List<String> none = child.getVariableArray("json_feature", "jsonObject");
        assertNull(none);
    }
}
