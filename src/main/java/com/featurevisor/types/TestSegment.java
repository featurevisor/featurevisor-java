package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a test segment
 */
public class TestSegment {
    @JsonProperty("key")
    private String key; // file path

    @JsonProperty("segment")
    private String segment;

    @JsonProperty("assertions")
    private List<SegmentAssertion> assertions;

    public TestSegment() {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public List<SegmentAssertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<SegmentAssertion> assertions) {
        this.assertions = assertions;
    }
}
