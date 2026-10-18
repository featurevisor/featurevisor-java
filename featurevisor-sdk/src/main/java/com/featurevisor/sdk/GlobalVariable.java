package com.featurevisor.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.List;

public class GlobalVariable {
    @JsonProperty("deprecated") private Boolean deprecated;
    public Boolean getDeprecated() { return deprecated; }
    public void setDeprecated(Boolean value) { deprecated = value; }
    @JsonProperty("compose") private Object compose;
    public Object getCompose() { return compose; }
    public void setCompose(Object value) { compose = value; }

    @JsonIgnore private Object conditions;
    private boolean conditionsPresent;
    @JsonIgnore
    public Object getConditions() { return conditions; }
    public void setConditions(Object value) { conditions = value; conditionsPresent = value != null; }
    boolean hasConditions() { return conditionsPresent; }
    @JsonSetter("conditions")
    private void readConditions(Object value) { conditions = value; conditionsPresent = true; }
    @JsonAnyGetter
    private java.util.Map<String, Object> conditionsProperty() {
        return conditionsPresent ? java.util.Collections.singletonMap("conditions", conditions) : java.util.Map.of();
    }

    @JsonProperty("hash") private String hash;
    @JsonProperty("type") private VariableType type;
    private Object defaultValue;
    private Object disabledValue;
    private boolean defaultValueSet;
    private boolean disabledValueSet;
    @JsonProperty("useDefaultWhenDisabled") private Boolean useDefaultWhenDisabled;
    @JsonProperty("requiredFeatures") private List<Object> requiredFeatures;
    @JsonProperty("requiredVariables") private Object requiredVariables;
    @JsonProperty("overrides") private List<VariableOverride> overrides;
    @JsonProperty("overrideProgram") private List<VariableOverride> overrideProgram;

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public VariableType getType() { return type; }
    public void setType(VariableType type) { this.type = type; }
    public Object getDefaultValue() { return defaultValue; }
    @JsonSetter(value = "defaultValue", nulls = Nulls.SET)
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; this.defaultValueSet = true; }
    public boolean hasDefaultValue() { return defaultValueSet; }
    public Object getDisabledValue() { return disabledValue; }
    @JsonSetter(value = "disabledValue", nulls = Nulls.SET)
    public void setDisabledValue(Object disabledValue) { this.disabledValue = disabledValue; this.disabledValueSet = true; }
    public boolean hasDisabledValue() { return disabledValueSet; }
    public Boolean getUseDefaultWhenDisabled() { return useDefaultWhenDisabled; }
    public void setUseDefaultWhenDisabled(Boolean value) { this.useDefaultWhenDisabled = value; }
    public List<Object> getRequiredFeatures() { return requiredFeatures; }
    public void setRequiredFeatures(List<Object> value) { this.requiredFeatures = value; }
    public Object getRequiredVariables() { return requiredVariables; }
    public void setRequiredVariables(Object value) { this.requiredVariables = value; }
    public List<VariableOverride> getOverrides() { return overrides; }
    public void setOverrides(List<VariableOverride> overrides) { this.overrides = overrides; }
    public List<VariableOverride> getOverrideProgram() { return overrideProgram; }
    public void setOverrideProgram(List<VariableOverride> value) { this.overrideProgram = value; }
}
