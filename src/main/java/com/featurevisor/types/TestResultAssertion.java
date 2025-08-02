package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a test result assertion
 */
public class TestResultAssertion {
    @JsonProperty("description")
    private String description;

    @JsonProperty("duration")
    private Long duration;

    @JsonProperty("passed")
    private Boolean passed;

    @JsonProperty("errors")
    private List<TestResultAssertionError> errors;

    public TestResultAssertion() {}

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public List<TestResultAssertionError> getErrors() {
        return errors;
    }

    public void setErrors(List<TestResultAssertionError> errors) {
        this.errors = errors;
    }
}
