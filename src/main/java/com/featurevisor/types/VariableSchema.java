package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VariableSchema {
    @JsonProperty("deprecated")
    private Boolean deprecated;

    @JsonProperty("key")
    private String key;

    @JsonProperty("type")
    private VariableType type;

    @JsonProperty("defaultValue")
    private Object defaultValue;

    @JsonProperty("description")
    private String description;

    @JsonProperty("useDefaultWhenDisabled")
    private Boolean useDefaultWhenDisabled;

    @JsonProperty("disabledValue")
    private Object disabledValue;

    // Constructors
    public VariableSchema() {}

    public VariableSchema(VariableType type, Object defaultValue) {
        this.type = type;
        this.defaultValue = defaultValue;
    }

    // Getters and Setters
    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public VariableType getType() {
        return type;
    }

    public void setType(VariableType type) {
        this.type = type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getUseDefaultWhenDisabled() {
        return useDefaultWhenDisabled;
    }

    public void setUseDefaultWhenDisabled(Boolean useDefaultWhenDisabled) {
        this.useDefaultWhenDisabled = useDefaultWhenDisabled;
    }

    public Object getDisabledValue() {
        return disabledValue;
    }

    public void setDisabledValue(Object disabledValue) {
        this.disabledValue = disabledValue;
    }
}
