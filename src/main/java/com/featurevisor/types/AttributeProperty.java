package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an attribute property
 */
public class AttributeProperty {
    @JsonProperty("type")
    private PropertyType type;

    @JsonProperty("description")
    private String description;

    // Constructors
    public AttributeProperty() {}

    public AttributeProperty(PropertyType type) {
        this.type = type;
    }

    // Getters and Setters
    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
