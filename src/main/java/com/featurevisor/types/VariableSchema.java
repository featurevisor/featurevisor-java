package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    @JsonProperty("additionalProperties")
    private Object additionalProperties;

    @JsonProperty("required")
    private List<String> required;

    @JsonProperty("items")
    private Object items;

    @JsonProperty("oneOf")
    private List<Object> oneOf;

    @JsonProperty("enum")
    private List<Object> enumValues;

    @JsonProperty("const")
    private Object constValue;

    @JsonProperty("minimum")
    private Double minimum;

    @JsonProperty("maximum")
    private Double maximum;

    @JsonProperty("minLength")
    private Integer minLength;

    @JsonProperty("maxLength")
    private Integer maxLength;

    @JsonProperty("pattern")
    private String pattern;

    @JsonProperty("minItems")
    private Integer minItems;

    @JsonProperty("maxItems")
    private Integer maxItems;

    @JsonProperty("uniqueItems")
    private Boolean uniqueItems;

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

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Object getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Object additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    public Object getItems() {
        return items;
    }

    public void setItems(Object items) {
        this.items = items;
    }

    public List<Object> getOneOf() {
        return oneOf;
    }

    public void setOneOf(List<Object> oneOf) {
        this.oneOf = oneOf;
    }

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<Object> enumValues) {
        this.enumValues = enumValues;
    }

    public Object getConstValue() {
        return constValue;
    }

    public void setConstValue(Object constValue) {
        this.constValue = constValue;
    }

    public Double getMinimum() {
        return minimum;
    }

    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    public void setMaximum(Double maximum) {
        this.maximum = maximum;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public void setMinItems(Integer minItems) {
        this.minItems = minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
    }

    public Boolean getUniqueItems() {
        return uniqueItems;
    }

    public void setUniqueItems(Boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
    }
}
