package com.featurevisor.types;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Custom deserializer for Bucket
 */
public class BucketDeserializer extends JsonDeserializer<Bucket> {
    @Override
    public Bucket deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isTextual()) {
            // Plain string bucketBy
            return new Bucket(node.asText());
        } else if (node.isArray()) {
            // Array bucketBy (treat as AND bucketBy)
            List<String> attributes = new ArrayList<>();
            for (JsonNode element : node) {
                attributes.add(element.asText());
            }
            return new Bucket(attributes, true);
        } else if (node.isObject()) {
            // Object bucketBy
            Bucket bucket = new Bucket();

            if (node.has("or")) {
                List<String> orAttributes = new ArrayList<>();
                JsonNode orNode = node.get("or");
                if (orNode.isArray()) {
                    for (JsonNode element : orNode) {
                        orAttributes.add(element.asText());
                    }
                }
                bucket.setOr(orAttributes);
            } else {
                // Treat as AND bucketBy if no "or" property
                List<String> andAttributes = new ArrayList<>();
                for (JsonNode element : node) {
                    andAttributes.add(element.asText());
                }
                bucket.setAndBucketBy(andAttributes);
            }

            return bucket;
        }

        throw new IOException("Bucket must be a string, array, or object");
    }
}
