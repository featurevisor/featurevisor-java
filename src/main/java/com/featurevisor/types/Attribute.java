package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import com.featurevisor.types.AttributeProperty;
import com.featurevisor.types.AttributeType;
import com.featurevisor.types.PropertyType;

public class Attribute {
    @JsonProperty("archived")
    private Boolean archived;

    @JsonProperty("key")
    private String key;

    @JsonProperty("type")
    private AttributeType type;

    @JsonProperty("description")
    private String description;

    @JsonProperty("properties")
    private Map<String, AttributeProperty> properties;

    // Constructors
    public Attribute() {}

    public Attribute(AttributeType type) {
        this.type = type;
    }

    // Getters and Setters
    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public AttributeType getType() {
        return type;
    }

    public void setType(AttributeType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, AttributeProperty> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, AttributeProperty> properties) {
        this.properties = properties;
    }


}
