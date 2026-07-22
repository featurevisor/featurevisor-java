package com.featurevisor.openfeature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featurevisor.sdk.Evaluation;
import com.featurevisor.sdk.Emitter;
import com.featurevisor.sdk.Featurevisor;
import com.featurevisor.sdk.FeaturevisorDiagnosticHandler;
import com.featurevisor.sdk.VariableType;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenFeature provider backed by the Featurevisor v3 SDK. */
public final class FeaturevisorOpenFeatureProvider implements FeatureProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @FunctionalInterface
    public interface TrackingHandler {
        void track(String eventName, EvaluationContext context, TrackingEventDetails details);
    }

    public static final class Options {
        private Featurevisor featurevisor;
        private Featurevisor.FeaturevisorOptions featurevisorOptions = new Featurevisor.FeaturevisorOptions();
        private String targetingKeyField = "userId";
        private String keySeparator = ":";
        private String variationKey = "variation";
        private TrackingHandler onTrack;

        public Options featurevisor(Featurevisor value) { this.featurevisor = value; return this; }
        public Options featurevisorOptions(Featurevisor.FeaturevisorOptions value) { this.featurevisorOptions = value; return this; }
        public Options targetingKeyField(String value) { this.targetingKeyField = value; return this; }
        public Options keySeparator(String value) { this.keySeparator = value; return this; }
        public Options variationKey(String value) { this.variationKey = value; return this; }
        public Options onTrack(TrackingHandler value) { this.onTrack = value; return this; }
    }

    private final Featurevisor featurevisor;
    private final String targetingKeyField;
    private final String keySeparator;
    private final String variationKey;
    private final TrackingHandler onTrack;
    private final Emitter.UnsubscribeFunction datafileUnsubscribe;
    private final boolean ownsFeaturevisor;
    private String datafileError;

    public FeaturevisorOpenFeatureProvider(Options options) {
        Options resolved = options != null ? options : new Options();
        this.targetingKeyField = nonEmpty(resolved.targetingKeyField, "userId");
        this.keySeparator = nonEmpty(resolved.keySeparator, ":");
        this.variationKey = nonEmpty(resolved.variationKey, "variation");
        this.onTrack = resolved.onTrack;
        this.ownsFeaturevisor = resolved.featurevisor == null;
        if (resolved.featurevisor != null) {
            this.featurevisor = resolved.featurevisor;
        } else {
            Featurevisor.FeaturevisorOptions fvOptions = resolved.featurevisorOptions != null
                    ? resolved.featurevisorOptions : new Featurevisor.FeaturevisorOptions();
            if (fvOptions.getDatafileString() != null) {
                try {
                    com.featurevisor.sdk.DatafileContent.fromJson(fvOptions.getDatafileString());
                } catch (Exception ignored) {
                    datafileError = "Could not parse datafile";
                }
            }
            FeaturevisorDiagnosticHandler original = fvOptions.getOnDiagnostic();
            fvOptions.onDiagnostic(diagnostic -> {
                if ("invalid_datafile".equals(diagnostic.getCode())) datafileError = diagnostic.getMessage();
                if ("datafile_set".equals(diagnostic.getCode())) datafileError = null;
                if (original != null) original.handle(diagnostic);
            });
            this.featurevisor = Featurevisor.createFeaturevisor(fvOptions);
        }
        this.datafileUnsubscribe = this.featurevisor.on(Emitter.EventName.DATAFILE_SET, details -> datafileError = null);
    }

    public FeaturevisorOpenFeatureProvider(Featurevisor.FeaturevisorOptions options) {
        this(new Options().featurevisorOptions(options));
    }

    public FeaturevisorOpenFeatureProvider(Featurevisor featurevisor) {
        this(new Options().featurevisor(featurevisor));
    }

    public Featurevisor getFeaturevisor() { return featurevisor; }
    @Override public Metadata getMetadata() { return () -> "Featurevisor"; }
    @Override public void shutdown() {
        datafileUnsubscribe.unsubscribe();
        if (ownsFeaturevisor) featurevisor.close();
    }
    @Override public void track(String name, EvaluationContext context, TrackingEventDetails details) {
        if (onTrack != null) onTrack.track(name, context, details);
    }

    @Override public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext context) {
        return cast(resolve(key, defaultValue, context, "boolean"), defaultValue, Boolean.class);
    }
    @Override public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext context) {
        return cast(resolve(key, defaultValue, context, "string"), defaultValue, String.class);
    }
    @Override public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext context) {
        ProviderEvaluation<Object> result = resolve(key, defaultValue, context, "integer");
        Integer value = result.getValue() instanceof Number ? ((Number) result.getValue()).intValue() : defaultValue;
        return copy(result, value);
    }
    @Override public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext context) {
        ProviderEvaluation<Object> result = resolve(key, defaultValue, context, "number");
        Double value = result.getValue() instanceof Number ? ((Number) result.getValue()).doubleValue() : defaultValue;
        return copy(result, value);
    }
    @Override public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext context) {
        ProviderEvaluation<Object> result = resolve(key, defaultValue, context, "object");
        try {
            return copy(result, toValue(result.getValue()));
        } catch (Exception exception) {
            return error(defaultValue, ErrorCode.TYPE_MISMATCH, "Flag \"" + key + "\" did not resolve to an object value", result.getFlagMetadata());
        }
    }

    private ProviderEvaluation<Object> resolve(String flagKey, Object defaultValue, EvaluationContext context, String expectedType) {
        if (datafileError != null) return error(defaultValue, ErrorCode.PARSE_ERROR, datafileError, ImmutableMetadata.EMPTY);
        int separatorIndex = flagKey.indexOf(keySeparator);
        String featureKey = separatorIndex < 0 ? flagKey : flagKey.substring(0, separatorIndex);
        String selector = separatorIndex < 0 ? null : flagKey.substring(separatorIndex + keySeparator.length());
        Map<String, Object> fvContext = context != null ? normalizeMap(context.asObjectMap()) : new HashMap<>();
        if (context != null && context.getTargetingKey() != null && !context.getTargetingKey().isEmpty()) {
            fvContext.put(targetingKeyField, context.getTargetingKey());
        }

        Evaluation evaluation;
        Object value;
        if (selector == null || selector.isEmpty()) {
            if (!"boolean".equals(expectedType)) return typeMismatch(flagKey, defaultValue, expectedType, ImmutableMetadata.EMPTY);
            evaluation = featurevisor.evaluateFlag(featureKey, fvContext);
            value = evaluation.getEnabled();
        } else if (selector.equals(variationKey)) {
            evaluation = featurevisor.evaluateVariation(featureKey, fvContext);
            value = evaluation.getVariationValue() != null ? evaluation.getVariationValue()
                    : evaluation.getVariation() != null ? evaluation.getVariation().getValue() : null;
        } else {
            evaluation = featurevisor.evaluateVariable(featureKey, selector, fvContext);
            value = evaluation.getVariableValue();
            if (evaluation.getVariableSchema() != null
                    && evaluation.getVariableSchema().getType() == VariableType.JSON
                    && value instanceof String) {
                try { value = OBJECT_MAPPER.readValue((String) value, Object.class); } catch (Exception ignored) { }
            }
        }

        ImmutableMetadata metadata = metadata(evaluation);
        ErrorCode errorCode = errorCode(evaluation.getReason());
        if (errorCode != null) return error(defaultValue, errorCode, errorMessage(evaluation), metadata);
        if (value == null) return success(defaultValue, evaluation, metadata);
        if (!matches(value, expectedType)) return typeMismatch(flagKey, defaultValue, expectedType, metadata);
        return success(value, evaluation, metadata);
    }

    private ProviderEvaluation<Object> success(Object value, Evaluation evaluation, ImmutableMetadata metadata) {
        return ProviderEvaluation.builder().value(value).variant(variant(evaluation)).reason(reason(evaluation.getReason()).name()).flagMetadata(metadata).build();
    }

    private ImmutableMetadata metadata(Evaluation evaluation) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder()
                .addString("featureKey", evaluation.getFeatureKey())
                .addString("featurevisorReason", evaluation.getReason())
                .addString("schemaVersion", featurevisor.getSchemaVersion());
        if (featurevisor.getRevision() != null) builder.addString("revision", featurevisor.getRevision());
        if (evaluation.getVariableKey() != null) builder.addString("variableKey", evaluation.getVariableKey());
        if (evaluation.getRuleKey() != null) builder.addString("ruleKey", evaluation.getRuleKey());
        if (evaluation.getBucketKey() != null) builder.addString("bucketKey", evaluation.getBucketKey());
        if (evaluation.getBucketValue() != null) builder.addInteger("bucketValue", evaluation.getBucketValue());
        if (evaluation.getForceIndex() != null) builder.addInteger("forceIndex", evaluation.getForceIndex());
        if (evaluation.getVariableOverrideIndex() != null) builder.addInteger("variableOverrideIndex", evaluation.getVariableOverrideIndex());
        return builder.build();
    }

    private static Reason reason(String reason) {
        if (List.of(Evaluation.REASON_FEATURE_NOT_FOUND, Evaluation.REASON_VARIABLE_NOT_FOUND, Evaluation.REASON_NO_VARIATIONS, Evaluation.REASON_ERROR).contains(reason)) return Reason.ERROR;
        if (List.of(Evaluation.REASON_REQUIRED, Evaluation.REASON_FORCED, Evaluation.REASON_STICKY, Evaluation.REASON_RULE, Evaluation.REASON_VARIABLE_OVERRIDE_RULE, Evaluation.REASON_VARIABLE_OVERRIDE_VARIATION).contains(reason)) return Reason.TARGETING_MATCH;
        if (Evaluation.REASON_ALLOCATED.equals(reason)) return Reason.SPLIT;
        if (List.of(Evaluation.REASON_DISABLED, Evaluation.REASON_VARIATION_DISABLED, Evaluation.REASON_VARIABLE_DISABLED).contains(reason)) return Reason.DISABLED;
        return Reason.DEFAULT;
    }

    private static ErrorCode errorCode(String reason) {
        if (List.of(Evaluation.REASON_FEATURE_NOT_FOUND, Evaluation.REASON_VARIABLE_NOT_FOUND, Evaluation.REASON_NO_VARIATIONS).contains(reason)) return ErrorCode.FLAG_NOT_FOUND;
        if (Evaluation.REASON_ERROR.equals(reason)) return ErrorCode.GENERAL;
        return null;
    }

    private static String errorMessage(Evaluation evaluation) {
        if (evaluation.getError() != null) return evaluation.getError().getMessage();
        if (Evaluation.REASON_FEATURE_NOT_FOUND.equals(evaluation.getReason())) return "Feature \"" + evaluation.getFeatureKey() + "\" was not found";
        if (Evaluation.REASON_VARIABLE_NOT_FOUND.equals(evaluation.getReason())) return "Variable \"" + evaluation.getVariableKey() + "\" was not found for feature \"" + evaluation.getFeatureKey() + "\"";
        if (Evaluation.REASON_NO_VARIATIONS.equals(evaluation.getReason())) return "Feature \"" + evaluation.getFeatureKey() + "\" has no variations";
        return "Featurevisor evaluation failed";
    }

    private static String variant(Evaluation evaluation) {
        if (evaluation.getVariationValue() != null) return evaluation.getVariationValue();
        return evaluation.getVariation() != null ? evaluation.getVariation().getValue() : null;
    }

    private static boolean matches(Object value, String expected) {
        if ("boolean".equals(expected)) return value instanceof Boolean;
        if ("string".equals(expected)) return value instanceof String;
        if ("integer".equals(expected)) return value instanceof Number && Math.rint(((Number) value).doubleValue()) == ((Number) value).doubleValue();
        if ("number".equals(expected)) return value instanceof Number && Double.isFinite(((Number) value).doubleValue());
        return value instanceof Map || value instanceof List;
    }

    private static Map<String, Object> normalizeMap(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        input.forEach((key, value) -> result.put(key, normalize(value)));
        return result;
    }
    private static Object normalize(Object value) {
        if (value instanceof Instant) return value.toString();
        if (value instanceof Map) return normalizeMap((Map<String, Object>) value);
        if (value instanceof List) { List<Object> result = new ArrayList<>(); for (Object item : (List<?>) value) result.add(normalize(item)); return result; }
        return value;
    }
    private static Value toValue(Object value) throws InstantiationException {
        if (value instanceof Value) return (Value) value;
        if (value instanceof Map) return new Value(Structure.mapToStructure((Map<String, Object>) value));
        if (value instanceof List) { List<Value> result = new ArrayList<>(); for (Object item : (List<?>) value) result.add(toValue(item)); return new Value(result); }
        return new Value(value);
    }
    private static ProviderEvaluation<Object> typeMismatch(String key, Object fallback, String expected, ImmutableMetadata metadata) {
        return error(fallback, ErrorCode.TYPE_MISMATCH, "Flag \"" + key + "\" did not resolve to a " + expected + " value", metadata);
    }
    private static <T> ProviderEvaluation<T> error(T value, ErrorCode code, String message, ImmutableMetadata metadata) {
        return ProviderEvaluation.<T>builder().value(value).reason(Reason.ERROR.name()).errorCode(code).errorMessage(message).flagMetadata(metadata).build();
    }
    private static <T> ProviderEvaluation<T> cast(ProviderEvaluation<Object> source, T fallback, Class<T> type) {
        T value = type.isInstance(source.getValue()) ? type.cast(source.getValue()) : fallback;
        return copy(source, value);
    }
    private static <T> ProviderEvaluation<T> copy(ProviderEvaluation<Object> source, T value) {
        return ProviderEvaluation.<T>builder().value(value).variant(source.getVariant()).reason(source.getReason()).errorCode(source.getErrorCode()).errorMessage(source.getErrorMessage()).flagMetadata(source.getFlagMetadata()).build();
    }
    private static String nonEmpty(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
}
