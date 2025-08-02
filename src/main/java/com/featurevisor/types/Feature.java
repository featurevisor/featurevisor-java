package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Feature {
    @JsonProperty("key")
    private String key;

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("deprecated")
    private Boolean deprecated;

    @JsonProperty("required")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<Object> required = new ArrayList<>(); // Can be String or RequiredWithVariation

    @JsonProperty("variablesSchema")
    private Map<String, VariableSchema> variablesSchema;

    @JsonProperty("disabledVariationValue")
    private String disabledVariationValue;

    @JsonProperty("variations")
    private List<Variation> variations;

    @JsonProperty("bucketBy")
    private Bucket bucketBy;

    @JsonProperty("traffic")
    private List<Traffic> traffic;

    @JsonProperty("force")
    private List<Force> force;

    @JsonProperty("ranges")
    private List<Range> ranges;

    // Constructors
    public Feature() {
    }

    public Feature(String key) {
        this.key = key;
    }

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public List<Object> getRequired() {
        if (this.required == null) {
            this.required = new ArrayList<>();
        }
        return this.required;
    }

    public void setRequired(List<Object> required) {
        this.required = (required != null) ? required : new ArrayList<>();
    }

    public Map<String, VariableSchema> getVariablesSchema() {
        return variablesSchema;
    }

    public void setVariablesSchema(Map<String, VariableSchema> variablesSchema) {
        this.variablesSchema = variablesSchema;
    }

    public String getDisabledVariationValue() {
        return disabledVariationValue;
    }

    public void setDisabledVariationValue(String disabledVariationValue) {
        this.disabledVariationValue = disabledVariationValue;
    }

    public List<Variation> getVariations() {
        return variations;
    }

    public void setVariations(List<Variation> variations) {
        this.variations = variations;
    }

    public Bucket getBucketBy() {
        return bucketBy;
    }

    public void setBucketBy(Bucket bucketBy) {
        this.bucketBy = bucketBy;
    }

    public List<Traffic> getTraffic() {
        return traffic;
    }

    public void setTraffic(List<Traffic> traffic) {
        this.traffic = traffic;
    }

    public List<Force> getForce() {
        return force;
    }

    public void setForce(List<Force> force) {
        this.force = force;
    }

    public List<Range> getRanges() {
        return ranges;
    }

    public void setRanges(List<Range> ranges) {
        this.ranges = ranges;
    }
}
