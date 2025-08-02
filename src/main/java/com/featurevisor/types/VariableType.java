package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum VariableType {
    @JsonProperty("boolean")
    BOOLEAN("boolean"),
    @JsonProperty("string")
    STRING("string"),
    @JsonProperty("integer")
    INTEGER("integer"),
    @JsonProperty("double")
    DOUBLE("double"),
    @JsonProperty("array")
    ARRAY("array"),
    @JsonProperty("object")
    OBJECT("object"),
    @JsonProperty("json")
    JSON("json");

    private final String value;

    VariableType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
