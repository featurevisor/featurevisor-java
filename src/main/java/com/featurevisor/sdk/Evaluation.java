package com.featurevisor.sdk;

import com.featurevisor.types.Bucket;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableSchema;
import java.util.Map;
import java.util.List;

/**
 * Evaluation result for Featurevisor SDK
 * Represents the result of evaluating a feature, variation, or variable
 */
public class Evaluation {
    // Evaluation types
    public static final String TYPE_FLAG = "flag";
    public static final String TYPE_VARIATION = "variation";
    public static final String TYPE_VARIABLE = "variable";

    // Evaluation reasons
    public static final String REASON_FEATURE_NOT_FOUND = "feature_not_found";
    public static final String REASON_DISABLED = "disabled";
    public static final String REASON_REQUIRED = "required";
    public static final String REASON_OUT_OF_RANGE = "out_of_range";
    public static final String REASON_NO_VARIATIONS = "no_variations";
    public static final String REASON_VARIATION_DISABLED = "variation_disabled";
    public static final String REASON_VARIABLE_NOT_FOUND = "variable_not_found";
    public static final String REASON_VARIABLE_DEFAULT = "variable_default";
    public static final String REASON_VARIABLE_DISABLED = "variable_disabled";
    public static final String REASON_VARIABLE_OVERRIDE = "variable_override";
    public static final String REASON_NO_MATCH = "no_match";
    public static final String REASON_FORCED = "forced";
    public static final String REASON_STICKY = "sticky";
    public static final String REASON_RULE = "rule";
    public static final String REASON_ALLOCATED = "allocated";
    public static final String REASON_ERROR = "error";

    // Required fields
    private String type;
    private String featureKey;
    private String reason;

    // Common fields
    private String bucketKey;
    private Integer bucketValue;
    private String ruleKey;
    private Exception error;
    private Boolean enabled;
    private Map<String, Object> traffic;
    private Integer forceIndex;
    private Map<String, Object> force;
    private List<Map<String, Object>> required;
    private Map<String, Object> sticky;

    // Variation fields
    private Variation variation;
    private String variationValue;

    // Variable fields
    private String variableKey;
    private Object variableValue;
    private VariableSchema variableSchema;

    // Required feature fields
    private String requiredFeatureKey;
    private String requiredVariation;
    private String actualVariation;

    // Constructors
    public Evaluation() {}

    public Evaluation(String type, String featureKey, String reason) {
        this.type = type;
        this.featureKey = featureKey;
        this.reason = reason;
    }

    // Getters
    public String getType() { return type; }
    public String getFeatureKey() { return featureKey; }
    public String getReason() { return reason; }
    public String getBucketKey() { return bucketKey; }
    public Integer getBucketValue() { return bucketValue; }
    public String getRuleKey() { return ruleKey; }
    public Exception getError() { return error; }
    public Boolean getEnabled() { return enabled; }
    public Map<String, Object> getTraffic() { return traffic; }
    public Integer getForceIndex() { return forceIndex; }
    public Map<String, Object> getForce() { return force; }
    public List<Map<String, Object>> getRequired() { return required; }
    public Map<String, Object> getSticky() { return sticky; }
    public Variation getVariation() { return variation; }
    public String getVariationValue() { return variationValue; }
    public String getVariableKey() { return variableKey; }
    public Object getVariableValue() { return variableValue; }
    public VariableSchema getVariableSchema() { return variableSchema; }
    public String getRequiredFeatureKey() { return requiredFeatureKey; }
    public String getRequiredVariation() { return requiredVariation; }
    public String getActualVariation() { return actualVariation; }

    // Setters
    public void setType(String type) { this.type = type; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public void setReason(String reason) { this.reason = reason; }
    public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
    public void setBucketValue(Integer bucketValue) { this.bucketValue = bucketValue; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public void setError(Exception error) { this.error = error; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public void setTraffic(Map<String, Object> traffic) { this.traffic = traffic; }
    public void setForceIndex(Integer forceIndex) { this.forceIndex = forceIndex; }
    public void setForce(Map<String, Object> force) { this.force = force; }
    public void setRequired(List<Map<String, Object>> required) { this.required = required; }
    public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
    public void setVariation(Variation variation) { this.variation = variation; }
    public void setVariationValue(String variationValue) { this.variationValue = variationValue; }
    public void setVariableKey(String variableKey) { this.variableKey = variableKey; }
    public void setVariableValue(Object variableValue) { this.variableValue = variableValue; }
    public void setVariableSchema(VariableSchema variableSchema) { this.variableSchema = variableSchema; }
    public void setRequiredFeatureKey(String requiredFeatureKey) { this.requiredFeatureKey = requiredFeatureKey; }
    public void setRequiredVariation(String requiredVariation) { this.requiredVariation = requiredVariation; }
    public void setActualVariation(String actualVariation) { this.actualVariation = actualVariation; }

    // Builder pattern methods
    public Evaluation type(String type) {
        this.type = type;
        return this;
    }

    public Evaluation featureKey(String featureKey) {
        this.featureKey = featureKey;
        return this;
    }

    public Evaluation reason(String reason) {
        this.reason = reason;
        return this;
    }

    public Evaluation bucketKey(String bucketKey) {
        this.bucketKey = bucketKey;
        return this;
    }

    public Evaluation bucketValue(Integer bucketValue) {
        this.bucketValue = bucketValue;
        return this;
    }

    public Evaluation ruleKey(String ruleKey) {
        this.ruleKey = ruleKey;
        return this;
    }

    public Evaluation error(Exception error) {
        this.error = error;
        return this;
    }

    public Evaluation enabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Evaluation traffic(Map<String, Object> traffic) {
        this.traffic = traffic;
        return this;
    }

    public Evaluation forceIndex(Integer forceIndex) {
        this.forceIndex = forceIndex;
        return this;
    }

    public Evaluation force(Map<String, Object> force) {
        this.force = force;
        return this;
    }

    public Evaluation required(List<Map<String, Object>> required) {
        this.required = required;
        return this;
    }

    public Evaluation sticky(Map<String, Object> sticky) {
        this.sticky = sticky;
        return this;
    }

    public Evaluation variation(Variation variation) {
        this.variation = variation;
        return this;
    }

    public Evaluation variationValue(String variationValue) {
        this.variationValue = variationValue;
        return this;
    }

    public Evaluation variableKey(String variableKey) {
        this.variableKey = variableKey;
        return this;
    }

    public Evaluation variableValue(Object variableValue) {
        this.variableValue = variableValue;
        return this;
    }

    public Evaluation variableSchema(VariableSchema variableSchema) {
        this.variableSchema = variableSchema;
        return this;
    }

    public Evaluation requiredFeatureKey(String requiredFeatureKey) {
        this.requiredFeatureKey = requiredFeatureKey;
        return this;
    }

    public Evaluation requiredVariation(String requiredVariation) {
        this.requiredVariation = requiredVariation;
        return this;
    }

    public Evaluation actualVariation(String actualVariation) {
        this.actualVariation = actualVariation;
        return this;
    }

    /**
     * Create a copy of this Evaluation
     * @return A new Evaluation instance with the same values
     */
    public Evaluation copy() {
        Evaluation copy = new Evaluation(this.type, this.featureKey, this.reason);
        copy.bucketKey = this.bucketKey;
        copy.bucketValue = this.bucketValue;
        copy.ruleKey = this.ruleKey;
        copy.error = this.error;
        copy.enabled = this.enabled;
        copy.traffic = this.traffic;
        copy.forceIndex = this.forceIndex;
        copy.force = this.force;
        copy.required = this.required;
        copy.sticky = this.sticky;
        copy.variation = this.variation;
        copy.variationValue = this.variationValue;
        copy.variableKey = this.variableKey;
        copy.variableValue = this.variableValue;
        copy.variableSchema = this.variableSchema;
        copy.requiredFeatureKey = this.requiredFeatureKey;
        copy.requiredVariation = this.requiredVariation;
        copy.actualVariation = this.actualVariation;
        return copy;
    }

    @Override
    public String toString() {
        return "Evaluation{" +
                "type='" + type + '\'' +
                ", featureKey='" + featureKey + '\'' +
                ", reason='" + reason + '\'' +
                ", bucketKey='" + bucketKey + '\'' +
                ", bucketValue=" + bucketValue +
                ", ruleKey='" + ruleKey + '\'' +
                ", error=" + error +
                ", enabled=" + enabled +
                ", traffic=" + traffic +
                ", forceIndex=" + forceIndex +
                ", force=" + force +
                ", required=" + required +
                ", sticky=" + sticky +
                ", variation=" + variation +
                ", variationValue=" + variationValue +
                ", variableKey='" + variableKey + '\'' +
                ", variableValue=" + variableValue +
                ", variableSchema=" + variableSchema +
                '}';
    }
}
