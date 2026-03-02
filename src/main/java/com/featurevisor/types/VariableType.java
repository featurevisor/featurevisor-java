package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

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

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VariableType fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (VariableType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        return null;
    }
}
