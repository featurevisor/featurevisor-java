package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Allocation {
    @JsonProperty("variation")
    private String variation;

    @JsonProperty("range")
    private Range range;

    // Constructors
    public Allocation() {}

    public Allocation(String variation, Range range) {
        this.variation = variation;
        this.range = range;
    }

    // Getters and Setters
    public String getVariation() {
        return variation;
    }

    public void setVariation(String variation) {
        this.variation = variation;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }
}
