package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.EvaluatedFeature;
import com.featurevisor.types.EvaluatedFeatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Main Featurevisor SDK class
 * Provides the primary interface for feature flag evaluation and factory methods
 */
public class Featurevisor {
    // from options
    private Map<String, Object> context = new HashMap<>();
    private Logger logger;
    private Map<String, Object> sticky;

    // internally created
    private DatafileReader datafileReader;
    private HooksManager hooksManager;
    private Emitter emitter;

    private static final DatafileContent emptyDatafile;

    static {
        emptyDatafile = new DatafileContent();
        emptyDatafile.setSchemaVersion("2");
        emptyDatafile.setRevision("unknown");
        emptyDatafile.setSegments(new HashMap<>());
        emptyDatafile.setFeatures(new HashMap<>());
    }

    /**
     * Factory methods
     */
    public static Featurevisor createInstance(Options options) {
        if (options == null) {
            options = new Options();
        }
        return new Featurevisor(options);
    }

    public static Featurevisor createInstance() {
        return createInstance(new Options());
    }

    public static Featurevisor createInstance(com.featurevisor.types.DatafileContent datafile) {
        return createInstance(new Options().datafile(datafile));
    }

    public static Featurevisor createInstance(String datafileString) {
        return createInstance(new Options().datafileString(datafileString));
    }

    public static Featurevisor createInstance(Map<String, Object> context) {
        return createInstance(new Options().context(context));
    }

    public static Featurevisor createInstance(Logger.LogLevel logLevel) {
        return createInstance(new Options().logLevel(logLevel));
    }

    public static Featurevisor createInstance(Logger logger) {
        return createInstance(new Options().logger(logger));
    }

    public static Featurevisor createInstance(Map<String, Object> sticky, boolean isSticky) {
        if (isSticky) {
            return createInstance(new Options().sticky(sticky));
        } else {
            return createInstance(new Options().context(sticky));
        }
    }

    /**
     * Options for creating an instance
     */
    public static class Options {
        private DatafileContent datafile;
        private String datafileString;
        private Map<String, Object> context;
        private Logger.LogLevel logLevel;
        private Logger logger;
        private Map<String, Object> sticky;
        private List<HooksManager.Hook> hooks;

        public Options() {}

        // Getters
        public DatafileContent getDatafile() { return datafile; }
        public String getDatafileString() { return datafileString; }
        public Map<String, Object> getContext() { return context; }
        public Logger.LogLevel getLogLevel() { return logLevel; }
        public Logger getLogger() { return logger; }
        public Map<String, Object> getSticky() { return sticky; }
        public List<HooksManager.Hook> getHooks() { return hooks; }

        // Setters
        public void setDatafile(DatafileContent datafile) { this.datafile = datafile; }
        public void setDatafileString(String datafileString) { this.datafileString = datafileString; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public void setLogLevel(Logger.LogLevel logLevel) { this.logLevel = logLevel; }
        public void setLogger(Logger logger) { this.logger = logger; }
        public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
        public void setHooks(List<HooksManager.Hook> hooks) { this.hooks = hooks; }

        // Builder pattern methods
        public Options datafile(DatafileContent datafile) {
            this.datafile = datafile;
            return this;
        }

        public Options datafileString(String datafileString) {
            this.datafileString = datafileString;
            return this;
        }

        public Options context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Options logLevel(Logger.LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Options logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Options sticky(Map<String, Object> sticky) {
            this.sticky = sticky;
            return this;
        }

        public Options hooks(List<HooksManager.Hook> hooks) {
            this.hooks = hooks;
            return this;
        }
    }

    /**
     * Options for overriding evaluation behavior
     */
    public static class OverrideOptions {
        private Map<String, Object> sticky;
        private String defaultVariationValue;
        private Object defaultVariableValue;
        private Evaluation flagEvaluation;

        public OverrideOptions() {}

        // Getters
        public Map<String, Object> getSticky() { return sticky; }
        public String getDefaultVariationValue() { return defaultVariationValue; }
        public Object getDefaultVariableValue() { return defaultVariableValue; }
        public Evaluation getFlagEvaluation() { return flagEvaluation; }

        // Setters
        public void setSticky(Map<String, Object> sticky) { this.sticky = sticky; }
        public void setDefaultVariationValue(String defaultVariationValue) { this.defaultVariationValue = defaultVariationValue; }
        public void setDefaultVariableValue(Object defaultVariableValue) { this.defaultVariableValue = defaultVariableValue; }
        public void setFlagEvaluation(Evaluation flagEvaluation) { this.flagEvaluation = flagEvaluation; }

        // Builder pattern methods
        public OverrideOptions sticky(Map<String, Object> sticky) {
            this.sticky = sticky;
            return this;
        }

        public OverrideOptions defaultVariationValue(String defaultVariationValue) {
            this.defaultVariationValue = defaultVariationValue;
            return this;
        }

        public OverrideOptions defaultVariableValue(Object defaultVariableValue) {
            this.defaultVariableValue = defaultVariableValue;
            return this;
        }

        public OverrideOptions flagEvaluation(Evaluation flagEvaluation) {
            this.flagEvaluation = flagEvaluation;
            return this;
        }
    }

    /**
     * Constructor
     */
    public Featurevisor(Options options) {
        // from options
        if (options.getContext() != null) {
            this.context = new HashMap<>(options.getContext());
        }

        this.logger = options.getLogger() != null ?
            options.getLogger() :
            Logger.createLogger(new Logger.CreateLoggerOptions().level(
                options.getLogLevel() != null ? options.getLogLevel() : Logger.LogLevel.INFO
            ));

        this.hooksManager = new HooksManager(new HooksManager.HooksManagerOptions(this.logger)
            .hooks(options.getHooks() != null ? options.getHooks() : new ArrayList<>()));

        this.emitter = new Emitter();
        this.sticky = options.getSticky();

        // datafile
        this.datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(emptyDatafile)
            .logger(this.logger));

        if (options.getDatafile() != null) {
            this.datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
                .datafile(options.getDatafile())
                .logger(this.logger));
        } else if (options.getDatafileString() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                DatafileContent datafile = mapper.readValue(options.getDatafileString(), DatafileContent.class);
                this.datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
                    .datafile(datafile)
                    .logger(this.logger));
            } catch (Exception e) {
                this.logger.error("could not parse datafile string", Map.of("error", e.getMessage()));
            }
        }

        this.logger.info("Featurevisor SDK initialized", null);
    }

    /**
     * Set log level
     */
    public void setLogLevel(Logger.LogLevel level) {
        this.logger.setLevel(level);
    }

    /**
     * Set datafile
     */
    public void setDatafile(DatafileContent datafile) {
        try {
            DatafileReader newDatafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
                .datafile(datafile)
                .logger(this.logger));

            Emitter.EventDetails details = Events.getParamsForDatafileSetEvent(
                this.datafileReader.getDatafile(), newDatafileReader.getDatafile());

            this.datafileReader = newDatafileReader;

            this.logger.info("datafile set", details);
            this.emitter.trigger(Emitter.EventName.DATAFILE_SET, details);
        } catch (Exception e) {
            this.logger.error("could not parse datafile", Map.of("error", e.getMessage()));
        }
    }

    /**
     * Set datafile from string
     */
    public void setDatafile(String datafileString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            DatafileContent datafile = mapper.readValue(datafileString, DatafileContent.class);
            setDatafile(datafile);
        } catch (Exception e) {
            this.logger.error("could not parse datafile string", Map.of("error", e.getMessage()));
        }
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

        this.logger.info("sticky features set", params);
        this.emitter.trigger(Emitter.EventName.STICKY_SET, params);
    }

    /**
     * Get revision
     */
    public String getRevision() {
        return this.datafileReader.getRevision();
    }

    /**
     * Get feature
     */
    public Feature getFeature(String featureKey) {
        return this.datafileReader.getFeature(featureKey);
    }

    /**
     * Add hook
     */
    public Runnable addHook(HooksManager.Hook hook) {
        return this.hooksManager.add(hook);
    }

    /**
     * Subscribe to event
     */
    public Emitter.UnsubscribeFunction on(Emitter.EventName eventName, Emitter.EventCallback callback) {
        return this.emitter.on(eventName, callback);
    }

    /**
     * Close instance
     */
    public void close() {
        this.emitter.clearAll();
    }

    /**
     * Context
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
        this.logger.debug(replace ? "context replaced" : "context updated", eventDetails);
    }

    public Map<String, Object> getContext(Map<String, Object> context) {
        if (context != null && !context.isEmpty()) {
            Map<String, Object> mergedContext = new HashMap<>(this.context);
            mergedContext.putAll(context);
            return mergedContext;
        }
        return new HashMap<>(this.context);
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(this.context);
    }

    /**
     * Spawn child instance
     */
    public ChildInstance spawn(Map<String, Object> context, OverrideOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (options == null) {
            options = new OverrideOptions();
        }

        return new ChildInstance(this, getContext(context), options.getSticky());
    }

    public ChildInstance spawn(Map<String, Object> context) {
        return spawn(context, null);
    }

    public ChildInstance spawn() {
        return spawn(null, null);
    }

    /**
     * Flag
     */
    private EvaluateOptions getEvaluationDependencies(Map<String, Object> context, OverrideOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (options == null) {
            options = new OverrideOptions();
        }

        Map<String, Object> mergedSticky = this.sticky;
        if (options.getSticky() != null) {
            if (this.sticky != null) {
                mergedSticky = new HashMap<>(this.sticky);
                mergedSticky.putAll(options.getSticky());
            } else {
                mergedSticky = options.getSticky();
            }
        }

        return new EvaluateOptions()
            .context(getContext(context))
            .logger(this.logger)
            .hooksManager(this.hooksManager)
            .datafileReader(this.datafileReader)
            .sticky(mergedSticky)
            .defaultVariationValue(options.getDefaultVariationValue())
            .defaultVariableValue(options.getDefaultVariableValue())
            .flagEvaluation(options.getFlagEvaluation());
    }

    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_FLAG)
            .featureKey(featureKey);

        return Evaluate.evaluateWithHooks(evaluateOptions);
    }

    public Evaluation evaluateFlag(String featureKey, Map<String, Object> context) {
        return evaluateFlag(featureKey, context, null);
    }

    public Evaluation evaluateFlag(String featureKey) {
        return evaluateFlag(featureKey, null, null);
    }

    public boolean isEnabled(String featureKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateFlag(featureKey, context, options);
            return Boolean.TRUE.equals(evaluation.getEnabled());
        } catch (Exception e) {
            this.logger.error("isEnabled", Map.of("featureKey", featureKey, "error", e.getMessage()));
            return false;
        }
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
    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_VARIATION)
            .featureKey(featureKey);

        return Evaluate.evaluateWithHooks(evaluateOptions);
    }

    public Evaluation evaluateVariation(String featureKey, Map<String, Object> context) {
        return evaluateVariation(featureKey, context, null);
    }

    public Evaluation evaluateVariation(String featureKey) {
        return evaluateVariation(featureKey, null, null);
    }

    public String getVariation(String featureKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateVariation(featureKey, context, options);

            if (evaluation.getVariationValue() != null) {
                return evaluation.getVariationValue();
            }

            if (evaluation.getVariation() != null) {
                return evaluation.getVariation().getValue();
            }

            return null;
        } catch (Exception e) {
            this.logger.error("getVariation", Map.of("featureKey", featureKey, "error", e.getMessage()));
            return null;
        }
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
    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        EvaluateOptions evaluateOptions = getEvaluationDependencies(context, options)
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey(featureKey)
            .variableKey(variableKey);

        return Evaluate.evaluateWithHooks(evaluateOptions);
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return evaluateVariable(featureKey, variableKey, context, null);
    }

    public Evaluation evaluateVariable(String featureKey, String variableKey) {
        return evaluateVariable(featureKey, variableKey, null, null);
    }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        try {
            Evaluation evaluation = evaluateVariable(featureKey, variableKey, context, options);

            if (evaluation.getVariableValue() != null) {
                Object value = evaluation.getVariableValue();
                if (value instanceof String) {
                    String strValue = (String) value;
                    boolean looksLikeJson = (strValue.startsWith("{") && strValue.endsWith("}")) ||
                                            (strValue.startsWith("[") && strValue.endsWith("]"));
                    boolean isJsonType = evaluation.getVariableSchema() != null &&
                                         "json".equals(evaluation.getVariableSchema().getType());
                    if (isJsonType || looksLikeJson) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            return mapper.readValue(strValue, Object.class);
                        } catch (Exception e) {
                            // fallback to string
                        }
                    }
                }
                return value;
            }

            return null;
        } catch (Exception e) {
            this.logger.error("getVariable", Map.of("featureKey", featureKey, "variableKey", variableKey, "error", e.getMessage()));
            return null;
        }
    }

    public Object getVariable(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariable(featureKey, variableKey, context, null);
    }

    public Object getVariable(String featureKey, String variableKey) {
        return getVariable(featureKey, variableKey, null, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "boolean");
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableBoolean(featureKey, variableKey, context, null);
    }

    public Boolean getVariableBoolean(String featureKey, String variableKey) {
        return getVariableBoolean(featureKey, variableKey, null, null);
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "string");
    }

    public String getVariableString(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableString(featureKey, variableKey, context, null);
    }

    public String getVariableString(String featureKey, String variableKey) {
        return getVariableString(featureKey, variableKey, null, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return (Integer) Helpers.getValueByType(variableValue, "integer");
    }

    public Integer getVariableInteger(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableInteger(featureKey, variableKey, context, null);
    }

    public Integer getVariableInteger(String featureKey, String variableKey) {
        return getVariableInteger(featureKey, variableKey, null, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return (Double) Helpers.getValueByType(variableValue, "double");
    }

    public Double getVariableDouble(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableDouble(featureKey, variableKey, context, null);
    }

    public Double getVariableDouble(String featureKey, String variableKey) {
        return getVariableDouble(featureKey, variableKey, null, null);
    }

    @SuppressWarnings("unchecked")
    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "array");
    }

    public List<String> getVariableArray(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableArray(featureKey, variableKey, context, null);
    }

    public List<String> getVariableArray(String featureKey, String variableKey) {
        return getVariableArray(featureKey, variableKey, null, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "object");
    }

    public <T> T getVariableObject(String featureKey, String variableKey, Map<String, Object> context) {
        return getVariableObject(featureKey, variableKey, context, null);
    }

    public <T> T getVariableObject(String featureKey, String variableKey) {
        return getVariableObject(featureKey, variableKey, null, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariableJSON(String featureKey, String variableKey, Map<String, Object> context, OverrideOptions options) {
        Object variableValue = getVariable(featureKey, variableKey, context, options);
        return Helpers.getValueByType(variableValue, "json");
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
    public EvaluatedFeatures getAllEvaluations(Map<String, Object> context, List<String> featureKeys, OverrideOptions options) {
        if (context == null) {
            context = new HashMap<>();
        }
        if (featureKeys == null) {
            featureKeys = new ArrayList<>();
        }
        if (options == null) {
            options = new OverrideOptions();
        }

        Map<String, EvaluatedFeature> result = new HashMap<>();

        List<String> keys = featureKeys.isEmpty() ? this.datafileReader.getFeatureKeys() : featureKeys;
        for (String featureKey : keys) {
            // isEnabled
            Evaluation flagEvaluation = evaluateFlag(featureKey, context, options);

            EvaluatedFeature evaluatedFeature = new EvaluatedFeature();
            evaluatedFeature.setEnabled(Boolean.TRUE.equals(flagEvaluation.getEnabled()));

            OverrideOptions opts = new OverrideOptions()
                .sticky(options.getSticky())
                .defaultVariationValue(options.getDefaultVariationValue())
                .defaultVariableValue(options.getDefaultVariableValue())
                .flagEvaluation(flagEvaluation);

            // variation
            if (this.datafileReader.hasVariations(featureKey)) {
                Object variation = getVariation(featureKey, context, opts);
                if (variation != null) {
                    evaluatedFeature.setVariation(variation.toString());
                }
            }

            // variables
            List<String> variableKeys = this.datafileReader.getVariableKeys(featureKey);
            if (!variableKeys.isEmpty()) {
                Map<String, Object> variables = new HashMap<>();

                for (String variableKey : variableKeys) {
                    variables.put(variableKey, getVariable(featureKey, variableKey, context, opts));
                }

                evaluatedFeature.setVariables(variables);
            }

            result.put(featureKey, evaluatedFeature);
        }

        return EvaluatedFeatures.of(result);
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
}
