package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

@JsonDeserialize(using = RangeDeserializer.class)
public class Range {
    // Range is represented as [Percentage, Percentage] where Percentage is 0 to 100,000
    // In Java, we'll represent this as a List<Integer> with exactly 2 elements

    @JsonProperty
    private List<Integer> range;

    // Constructors
    public Range() {}

    public Range(Integer start, Integer end) {
        this.range = List.of(start, end);
    }

    public Range(List<Integer> range) {
        if (range.size() != 2) {
            throw new IllegalArgumentException("Range must contain exactly 2 elements");
        }
        this.range = range;
    }

    // Getters and Setters
    public List<Integer> getRange() {
        return range;
    }

    public void setRange(List<Integer> range) {
        if (range.size() != 2) {
            throw new IllegalArgumentException("Range must contain exactly 2 elements");
        }
        this.range = range;
    }

    // Helper methods
    public Integer getStart() {
        return range != null && range.size() >= 1 ? range.get(0) : null;
    }

    public Integer getEnd() {
        return range != null && range.size() >= 2 ? range.get(1) : null;
    }

    public void setStart(Integer start) {
        if (range == null) {
            range = List.of(start, 0);
        } else {
            range.set(0, start);
        }
    }

    public void setEnd(Integer end) {
        if (range == null) {
            range = List.of(0, end);
        } else {
            range.set(1, end);
        }
    }


}
