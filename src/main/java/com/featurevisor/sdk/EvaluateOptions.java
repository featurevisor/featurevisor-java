package com.featurevisor.sdk;

import com.featurevisor.types.VariableSchema;
import java.util.Map;

/**
 * Options for evaluation in Featurevisor SDK
 * Contains all the parameters needed for evaluating a feature, variation, or variable
 */
public class EvaluateOptions {
    // Evaluation parameters
    private String type;
    private String featureKey;
    private String variableKey;

    // Dependencies
    private Map<String, Object> context;
    private Logger logger;
    private HooksManager hooksManager;
    private DatafileReader datafileReader;

    // Override options
    private Map<String, Object> sticky;
    private String defaultVariationValue;
    private Object defaultVariableValue;
    private Evaluation flagEvaluation;

    // Constructors
    public EvaluateOptions() {}

    public EvaluateOptions(String type, String featureKey) {
        this.type = type;
        this.featureKey = featureKey;
    }

    public EvaluateOptions(String type, String featureKey, String variableKey) {
        this.type = type;
        this.featureKey = featureKey;
        this.variableKey = variableKey;
    }

    // Getters
    public String getType() { return type; }
    public String getFeatureKey() { return featureKey; }
    public String getVariableKey() { return variableKey; }
    public Map<String, Object> getContext() { return context; }
    public Logger getLogger() { return logger; }
    public HooksManager getHooksManager() { return hooksManager; }
    public DatafileReader getDatafileReader() { return datafileReader; }
    public Map<String, Object> getSticky() { return sticky; }
    public String getDefaultVariationValue() { return defaultVariationValue; }
    public Object getDefaultVariableValue() { return defaultVariableValue; }
    public Evaluation getFlagEvaluation() { return flagEvaluation; }

    // Setters
    public void setType(String type) { this.type = type; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public void setVariableKey(String variableKey) { this.variableKey = variableKey; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setLogger(Logger logger) { this.logger = logger; }
    public void setHooksManager(HooksManager hooksManager) { this.hooksManager = hooksManager; }
    public void setDatafileReader(DatafileReader datafileReader) { this.datafileReader = datafileReader; }
    public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
    public void setDefaultVariationValue(String defaultVariationValue) { this.defaultVariationValue = defaultVariationValue; }
    public void setDefaultVariableValue(Object defaultVariableValue) { this.defaultVariableValue = defaultVariableValue; }
    public void setFlagEvaluation(Evaluation flagEvaluation) { this.flagEvaluation = flagEvaluation; }

    // Builder pattern methods
    public EvaluateOptions type(String type) {
        this.type = type;
        return this;
    }

    public EvaluateOptions featureKey(String featureKey) {
        this.featureKey = featureKey;
        return this;
    }

    public EvaluateOptions variableKey(String variableKey) {
        this.variableKey = variableKey;
        return this;
    }

    public EvaluateOptions context(Map<String, Object> context) {
        this.context = context;
        return this;
    }

    public EvaluateOptions logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public EvaluateOptions hooksManager(HooksManager hooksManager) {
        this.hooksManager = hooksManager;
        return this;
    }

    public EvaluateOptions datafileReader(DatafileReader datafileReader) {
        this.datafileReader = datafileReader;
        return this;
    }

    public EvaluateOptions sticky(Map<String, Object> sticky) {
        this.sticky = sticky;
        return this;
    }

    public EvaluateOptions defaultVariationValue(String defaultVariationValue) {
        this.defaultVariationValue = defaultVariationValue;
        return this;
    }

    public EvaluateOptions defaultVariableValue(Object defaultVariableValue) {
        this.defaultVariableValue = defaultVariableValue;
        return this;
    }

    public EvaluateOptions flagEvaluation(Evaluation flagEvaluation) {
        this.flagEvaluation = flagEvaluation;
        return this;
    }

    /**
     * Create a copy of this EvaluateOptions with new values
     * @return A new EvaluateOptions instance with the same values
     */
    public EvaluateOptions copy() {
        EvaluateOptions copy = new EvaluateOptions();
        copy.type = this.type;
        copy.featureKey = this.featureKey;
        copy.variableKey = this.variableKey;
        copy.context = this.context;
        copy.logger = this.logger;
        copy.hooksManager = this.hooksManager;
        copy.datafileReader = this.datafileReader;
        copy.sticky = this.sticky;
        copy.defaultVariationValue = this.defaultVariationValue;
        copy.defaultVariableValue = this.defaultVariableValue;
        copy.flagEvaluation = this.flagEvaluation;
        return copy;
    }

    /**
     * Create a copy of this EvaluateOptions with a new type
     * @param type The new type
     * @return A new EvaluateOptions instance with the new type
     */
    public EvaluateOptions withType(String type) {
        EvaluateOptions copy = copy();
        copy.type = type;
        return copy;
    }

    @Override
    public String toString() {
        return "EvaluateOptions{" +
                "type='" + type + '\'' +
                ", featureKey='" + featureKey + '\'' +
                ", variableKey='" + variableKey + '\'' +
                ", context=" + context +
                ", logger=" + logger +
                ", hooksManager=" + hooksManager +
                ", datafileReader=" + datafileReader +
                ", sticky=" + sticky +
                ", defaultVariationValue=" + defaultVariationValue +
                ", defaultVariableValue=" + defaultVariableValue +
                ", flagEvaluation=" + flagEvaluation +
                '}';
    }
}
