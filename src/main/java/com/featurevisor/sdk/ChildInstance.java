package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.featurevisor.types.EvaluatedFeatures;

/**
 * Child instance of Featurevisor SDK
 * Provides isolated context for individual requests/users
 */
public class ChildInstance {
    private FeaturevisorInstance parent;
    private Map<String, Object> context;
    private Map<String, Object> sticky;
    private Emitter emitter;

    /**
     * Constructor
     */
    public ChildInstance(FeaturevisorInstance parent, Map<String, Object> context, Map<String, Object> sticky) {
        this.parent = parent;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.sticky = sticky;
        this.emitter = new Emitter();
    }

    /**
     * Subscribe to event
     */
    public Emitter.UnsubscribeFunction on(Emitter.EventName eventName, Emitter.EventCallback callback) {
        if (Emitter.EventName.CONTEXT_SET.equals(eventName) || Emitter.EventName.STICKY_SET.equals(eventName)) {
            return this.emitter.on(eventName, callback);
        }

        return this.parent.on(eventName, callback);
    }

    /**
     * Close instance
     */
    public void close() {
        this.emitter.clearAll();
    }

    /**
     * Set context
     */
    public void setContext(Map<String, Object> context, boolean replace) {
        if (replace) {
            this.context = new HashMap<>(context);
        } else {
            this.context = new HashMap<>(this.context);
            this.context.putAll(context);
        }

        Emitter.EventDetails eventDetails = new Emitter.EventDetails();
        eventDetails.put("context", this.context);
        eventDetails.put("replaced", replace);

        this.emitter.trigger(Emitter.EventName.CONTEXT_SET, eventDetails);
    }

    public void setContext(Map<String, Object> context) {
        setContext(context, false);
    }

    /**
     * Get context
     */
    public Map<String, Object> getContext(Map<String, Object> context) {
        return this.parent.getContext(mergeContexts(this.context, context));
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(this.context);
    }

    /**
     * Set sticky features
     */
    public void setSticky(Map<String, Object> sticky, boolean replace) {
        Map<String, Object> previousStickyFeatures = this.sticky != null ?
            new HashMap<>(this.sticky) : new HashMap<>();

        if (replace) {
            this.sticky = new HashMap<>(sticky);
        } else {
            this.sticky = new HashMap<>(this.sticky != null ? this.sticky : new HashMap<>());
            this.sticky.putAll(sticky);
        }

        Emitter.EventDetails params = Events.getParamsForStickySetEvent(
            previousStickyFeatures, this.sticky, replace);

        this.emitter.trigger(Emitter.EventName.STICKY_SET, params);
    }

    public void setSticky(Map<String, Object> sticky) {
        setSticky(sticky, false);
    }

    /**
     * Flag
     */
    public boolean isEnabled(String featureKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.isEnabled(
            featureKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public boolean isEnabled(String featureKey, Map<String, Object> context) {
        return isEnabled(featureKey, context, null);
    }

    public boolean isEnabled(String featureKey) {
        return isEnabled(featureKey, null, null);
    }

    /**
     * Variation
     */
    public String getVariation(String featureKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariation(
            featureKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public String getVariation(String featureKey, Map<String, Object> context) {
        return getVariation(featureKey, context, null);
    }

    public String getVariation(String featureKey) {
        return getVariation(featureKey, null, null);
    }

    /**
     * Variable
     */
    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariable(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariable(featureKey, variableKey, context, null);
    }

    public Object getVariable(String featureKey, String variableKey) {
        return getVariable(featureKey, variableKey, null, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableBoolean(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableBoolean(featureKey, variableKey, context, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey) {
        return getVariableBoolean(featureKey, variableKey, null, null);
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableString(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableString(featureKey, variableKey, context, null);
    }

    public String getVariableString(String featureKey, String variableKey) {
        return getVariableString(featureKey, variableKey, null, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableInteger(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableInteger(featureKey, variableKey, context, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey) {
        return getVariableInteger(featureKey, variableKey, null, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableDouble(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableDouble(featureKey, variableKey, context, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey) {
        return getVariableDouble(featureKey, variableKey, null, null);
    }

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableArray(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableArray(featureKey, variableKey, context, null);
    }

    public List<String> getVariableArray(String featureKey, String variableKey) {
        return getVariableArray(featureKey, variableKey, null, null);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableObject(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableObject(featureKey, variableKey, context, null);
    }

    public <T> T getVariableObject(String featureKey, String variableKey) {
        return getVariableObject(featureKey, variableKey, null, null);
    }

    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getVariableJSON(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableJSON(featureKey, variableKey, context, null);
    }

    public <T> T getVariableJSON(String featureKey, String variableKey) {
        return getVariableJSON(featureKey, variableKey, null, null);
    }

    /**
     * Get all evaluations
     */
    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys, FeaturevisorInstance.OverrideOptions options) {
        return this.parent.getAllEvaluations(
            mergeContexts(this.context, context),
            featureKeys,
            mergeOverrideOptions(options)
        );
    }

    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys) {
        return getAllEvaluations(context, featureKeys, null);
    }

    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context) {
        return getAllEvaluations(context, null, null);
    }

    public EvaluatedFeatures getAllEvaluations() {
        return getAllEvaluations(null, null, null);
    }

    /**
     * Helper methods
     */
    private Map<String, Object> mergeContexts(Map<String, Object> childContext, Map<String, Object> additionalContext) {
        if (additionalContext == null || additionalContext.isEmpty()) {
            return childContext;
        }

        Map<String, Object> merged = new HashMap<>(childContext);
        merged.putAll(additionalContext);
        return merged;
    }

    private FeaturevisorInstance.OverrideOptions mergeOverrideOptions(FeaturevisorInstance.OverrideOptions options) {
        if (options == null) {
            options = new FeaturevisorInstance.OverrideOptions();
        }

        if (this.sticky != null) {
            Map<String, Object> mergedSticky = new HashMap<>(this.sticky);
            if (options.getSticky() != null) {
                mergedSticky.putAll(options.getSticky());
            }
            options.sticky(mergedSticky);
        }

        return options;
    }
}
