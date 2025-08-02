package com.featurevisor.types;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Custom deserializer for Range
 */
public class RangeDeserializer extends JsonDeserializer<Range> {
    @Override
    public Range deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isArray()) {
            List<Integer> rangeList = new ArrayList<>();
            for (JsonNode element : node) {
                rangeList.add(element.asInt());
            }
            return new Range(rangeList);
        } else if (node.has("range")) {
            JsonNode rangeNode = node.get("range");
            if (rangeNode.isArray()) {
                List<Integer> rangeList = new ArrayList<>();
                for (JsonNode element : rangeNode) {
                    rangeList.add(element.asInt());
                }
                return new Range(rangeList);
            }
        }

        throw new IOException("Range must be an array of two integers");
    }
}
