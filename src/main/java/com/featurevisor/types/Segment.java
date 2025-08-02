package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.featurevisor.types.GroupSegment;
import java.util.List;

public class Segment {
    @JsonProperty("archived")
    private Boolean archived;

    @JsonProperty("key")
    private String key;

    @JsonProperty("conditions")
    private Object conditions; // Can be Condition, List<Condition>, or String

    @JsonProperty("description")
    private String description;

    // Constructors
    public Segment() {}

    public Segment(String key) {
        this.key = key;
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

    public Object getConditions() {
        return conditions;
    }

    public void setConditions(Object conditions) {
        this.conditions = conditions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}
