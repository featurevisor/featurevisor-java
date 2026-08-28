package com.featurevisor.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class GlobalVariable {
    @JsonProperty("hash") private String hash;
    @JsonProperty("type") private VariableType type;
    @JsonProperty("defaultValue") private Object defaultValue;
    @JsonProperty("disabledValue") private Object disabledValue;
    @JsonProperty("useDefaultWhenDisabled") private Boolean useDefaultWhenDisabled;
    @JsonProperty("requiredFeatures") private List<Object> requiredFeatures;
    @JsonProperty("overrides") private List<VariableOverride> overrides;

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public VariableType getType() { return type; }
    public void setType(VariableType type) { this.type = type; }
    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    public Object getDisabledValue() { return disabledValue; }
    public void setDisabledValue(Object disabledValue) { this.disabledValue = disabledValue; }
    public Boolean getUseDefaultWhenDisabled() { return useDefaultWhenDisabled; }
    public void setUseDefaultWhenDisabled(Boolean value) { this.useDefaultWhenDisabled = value; }
    public List<Object> getRequiredFeatures() { return requiredFeatures; }
    public void setRequiredFeatures(List<Object> value) { this.requiredFeatures = value; }
    public List<VariableOverride> getOverrides() { return overrides; }
    public void setOverrides(List<VariableOverride> overrides) { this.overrides = overrides; }
}
