package com.featurevisor.sdk;

import com.featurevisor.sdk.VariableSchema;
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
    private DiagnosticReporter diagnostics;
    private ModulesManager modulesManager;
    private InstanceEvaluationDataProvider evaluationData;

    // Override options
    private Map<String, Object> sticky;
    private String defaultVariationValue;
    private Object defaultVariableValue;
    private boolean defaultVariableValueSet;

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
    DiagnosticReporter getDiagnostics() { return diagnostics; }
    ModulesManager getModulesManager() { return modulesManager; }
    InstanceEvaluationDataProvider getInstanceEvaluationDataProvider() { return evaluationData; }
    public Map<String, Object> getSticky() { return sticky; }
    public String getDefaultVariationValue() { return defaultVariationValue; }
    public Object getDefaultVariableValue() { return defaultVariableValue; }
    public boolean hasDefaultVariableValue() { return defaultVariableValueSet; }

    // Setters
    public void setType(String type) { this.type = type; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public void setVariableKey(String variableKey) { this.variableKey = variableKey; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    void setDiagnostics(DiagnosticReporter diagnostics) { this.diagnostics = diagnostics; }
    void setModulesManager(ModulesManager modulesManager) { this.modulesManager = modulesManager; }
    void setInstanceEvaluationDataProvider(InstanceEvaluationDataProvider evaluationData) { this.evaluationData = evaluationData; }
    public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
    public void setDefaultVariationValue(String defaultVariationValue) { this.defaultVariationValue = defaultVariationValue; }
    public void setDefaultVariableValue(Object defaultVariableValue) {
        this.defaultVariableValue = defaultVariableValue;
        this.defaultVariableValueSet = true;
    }

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

    EvaluateOptions diagnostics(DiagnosticReporter diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    EvaluateOptions modulesManager(ModulesManager modulesManager) {
        this.modulesManager = modulesManager;
        return this;
    }

    EvaluateOptions evaluationData(InstanceEvaluationDataProvider evaluationData) {
        this.evaluationData = evaluationData;
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
        this.defaultVariableValueSet = true;
        return this;
    }

    EvaluateOptions defaultVariableValue(Object defaultVariableValue, boolean isSet) {
        this.defaultVariableValue = defaultVariableValue;
        this.defaultVariableValueSet = isSet;
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
        copy.diagnostics = this.diagnostics;
        copy.modulesManager = this.modulesManager;
        copy.evaluationData = this.evaluationData;
        copy.sticky = this.sticky;
        copy.defaultVariationValue = this.defaultVariationValue;
        copy.defaultVariableValue = this.defaultVariableValue;
        copy.defaultVariableValueSet = this.defaultVariableValueSet;
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
                ", diagnostics=" + diagnostics +
                ", modulesManager=" + modulesManager +
                ", evaluationData=" + evaluationData +
                ", sticky=" + sticky +
                ", defaultVariationValue=" + defaultVariationValue +
                ", defaultVariableValue=" + defaultVariableValue +
                '}';
    }
}
