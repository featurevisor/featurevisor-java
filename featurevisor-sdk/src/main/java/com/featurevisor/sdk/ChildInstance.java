package com.featurevisor.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import com.featurevisor.sdk.EvaluatedFeatures;

/**
 * Child instance of Featurevisor SDK
 * Provides isolated context for individual requests/users
 */
public class ChildInstance {
    private Featurevisor parent;
    private Map<String, Object> context;
    private Map<String, Object> sticky;
    private Emitter emitter;
    private final List<FeaturevisorUnsubscribe> parentUnsubscribers = new ArrayList<>();

    /**
     * Constructor
     */
    ChildInstance(Featurevisor parent, Map<String, Object> context, Map<String, Object> sticky) {
        this.parent = parent;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.sticky = sticky;
        this.emitter = new Emitter();
    }

    /**
     * Subscribe to event
     */
    public FeaturevisorUnsubscribe on(FeaturevisorEventName eventName, FeaturevisorEventHandler callback) {
        if (FeaturevisorEventName.CONTEXT_SET.equals(eventName) || FeaturevisorEventName.STICKY_SET.equals(eventName)) {
            return this.emitter.on(eventName, callback);
        }

        FeaturevisorUnsubscribe parentUnsubscribe = this.parent.on(eventName, callback);
        final boolean[] active = {true};
        final FeaturevisorUnsubscribe[] holder = new FeaturevisorUnsubscribe[1];
        holder[0] = () -> {
            if (!active[0]) {
                return;
            }
            active[0] = false;
            parentUnsubscribe.unsubscribe();
            this.parentUnsubscribers.remove(holder[0]);
        };
        this.parentUnsubscribers.add(holder[0]);
        return holder[0];
    }

    /**
     * Close instance
     */
    public void close() {
        for (FeaturevisorUnsubscribe unsubscribe : new ArrayList<>(this.parentUnsubscribers)) {
            unsubscribe.unsubscribe();
        }
        this.parentUnsubscribers.clear();
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

        FeaturevisorEventDetails eventDetails = new FeaturevisorEventDetails();
        eventDetails.put("context", this.context);
        eventDetails.put("replaced", replace);

        this.emitter.trigger(FeaturevisorEventName.CONTEXT_SET, eventDetails);
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
        return this.parent.getContext(new HashMap<>(this.context));
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

        FeaturevisorEventDetails params = Events.getParamsForStickySetEvent(
            previousStickyFeatures, this.sticky, replace);

        this.emitter.trigger(FeaturevisorEventName.STICKY_SET, params);
    }

    public void setSticky(Map<String, Object> sticky) {
        setSticky(sticky, false);
    }

    /**
     * Flag
     */
    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        return this.parent.evaluateFlag(
            featureKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context) {
        return evaluateFlag(featureKey, context, null);
    }

    public Evaluation evaluateFlag(String featureKey) {
        return evaluateFlag(featureKey, null, null);
    }

    public boolean isEnabled(String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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
    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        return this.parent.evaluateVariation(
            featureKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context) {
        return evaluateVariation(featureKey, context, null);
    }

    public Evaluation evaluateVariation(String featureKey) {
        return evaluateVariation(featureKey, null, null);
    }

    public String getVariation(String featureKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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
    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        return this.parent.evaluateVariable(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return evaluateVariable(featureKey, variableKey, context, null);
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey) {
        return evaluateVariable(featureKey, variableKey, null, null);
    }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        return this.parent.getVariableArray(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableArray(featureKey, variableKey, context, (Featurevisor.OverrideOptions) null);
    }

    public List<String> getVariableArray(String featureKey, String variableKey) {
        return getVariableArray(featureKey, variableKey, null, (Featurevisor.OverrideOptions) null);
    }

    public <T> List<T> getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Featurevisor.OverrideOptions options,
        Class<T> itemType
    ) {
        return this.parent.getVariableArray(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options),
            itemType
        );
    }

    public <T> List<T> getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Class<T> itemType
    ) {
        return getVariableArray(featureKey, variableKey, context, null, itemType);
    }

    public <T> List<T> getVariableArray(String featureKey, String variableKey, Class<T> itemType) {
        return getVariableArray(featureKey, variableKey, null, null, itemType);
    }

    public <T> T getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Featurevisor.OverrideOptions options,
        TypeReference<T> typeRef
    ) {
        return this.parent.getVariableArray(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options),
            typeRef
        );
    }

    public <T> T getVariableArray(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        TypeReference<T> typeRef
    ) {
        return getVariableArray(featureKey, variableKey, context, null, typeRef);
    }

    public <T> T getVariableArray(String featureKey, String variableKey, TypeReference<T> typeRef) {
        return getVariableArray(featureKey, variableKey, null, null, typeRef);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
        return this.parent.getVariableObject(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableObject(featureKey, variableKey, context, (Featurevisor.OverrideOptions) null);
    }

    public <T> T getVariableObject(String featureKey, String variableKey) {
        return getVariableObject(featureKey, variableKey, null, (Featurevisor.OverrideOptions) null);
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Featurevisor.OverrideOptions options,
        Class<T> type
    ) {
        return this.parent.getVariableObject(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options),
            type
        );
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Class<T> type
    ) {
        return getVariableObject(featureKey, variableKey, context, null, type);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Class<T> type) {
        return getVariableObject(featureKey, variableKey, null, null, type);
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Featurevisor.OverrideOptions options,
        TypeReference<T> typeRef
    ) {
        return this.parent.getVariableObject(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options),
            typeRef
        );
    }

    public <T> T getVariableObject(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        TypeReference<T> typeRef
    ) {
        return getVariableObject(featureKey, variableKey, context, null, typeRef);
    }

    public <T> T getVariableObject(String featureKey, String variableKey, TypeReference<T> typeRef) {
        return getVariableObject(featureKey, variableKey, null, null, typeRef);
    }

    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context, Featurevisor.OverrideOptions options) {
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

    public JsonNode getVariableJSONNode(
        String featureKey,
        String variableKey,
        Map<String, Object> context,
        Featurevisor.OverrideOptions options
    ) {
        return this.parent.getVariableJSONNode(
            featureKey,
            variableKey,
            mergeContexts(this.context, context),
            mergeOverrideOptions(options)
        );
    }

    public JsonNode getVariableJSONNode(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableJSONNode(featureKey, variableKey, context, null);
    }

    public JsonNode getVariableJSONNode(String featureKey, String variableKey) {
        return getVariableJSONNode(featureKey, variableKey, null, null);
    }

    /**
     * Get all evaluations
     */
    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys, Featurevisor.OverrideOptions options) {
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

    private Featurevisor.OverrideOptions mergeOverrideOptions(Featurevisor.OverrideOptions options) {
        if (options == null) {
            options = new Featurevisor.OverrideOptions();
        }

        options.setInternalSticky(this.sticky);

        return options;
    }
}
