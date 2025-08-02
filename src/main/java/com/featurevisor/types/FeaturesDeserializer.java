package com.featurevisor.types;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Custom deserializer for features map to ensure Feature objects are properly instantiated
 */
public class FeaturesDeserializer extends JsonDeserializer<Map<String, Feature>> {
    @Override
    public Map<String, Feature> deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
        JsonNode node = p.getCodec().readTree(p);
        Map<String, Feature> features = new HashMap<>();

        if (node.isObject()) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                try {
                    Feature feature = mapper.treeToValue(valueNode, Feature.class);
                    features.put(key, feature);
                } catch (Exception e) {
                    // Create a default Feature instance
                    Feature feature = new Feature(key);
                    features.put(key, feature);
                }
            }
        }

        return features;
    }
}
