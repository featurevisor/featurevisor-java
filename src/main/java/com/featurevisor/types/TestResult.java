package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a test result
 */
public class TestResult {
    @JsonProperty("type")
    private String type; // "feature" or "segment"

    @JsonProperty("key")
    private String key;

    @JsonProperty("notFound")
    private Boolean notFound;

    @JsonProperty("passed")
    private Boolean passed;

    @JsonProperty("duration")
    private Long duration;

    @JsonProperty("assertions")
    private List<TestResultAssertion> assertions;

    public TestResult() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Boolean getNotFound() {
        return notFound;
    }

    public void setNotFound(Boolean notFound) {
        this.notFound = notFound;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public List<TestResultAssertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<TestResultAssertion> assertions) {
        this.assertions = assertions;
    }
}
