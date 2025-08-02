package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.Force;
import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableOverride;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Evaluates forced features and returns appropriate evaluation results
 * This class handles the logic for evaluating forced features, variables, and variations
 */
public class EvaluateForced {

    /**
     * Result of forced evaluation containing evaluation, force, and forceIndex
     */
    public static class EvaluateForcedResult {
        private Evaluation evaluation;
        private Force force;
        private Integer forceIndex;

        public EvaluateForcedResult() {}

        public EvaluateForcedResult(Evaluation evaluation, Force force, Integer forceIndex) {
            this.evaluation = evaluation;
            this.force = force;
            this.forceIndex = forceIndex;
        }

        // Getters and Setters
        public Evaluation getEvaluation() { return evaluation; }
        public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }
        public Force getForce() { return force; }
        public void setForce(Force force) { this.force = force; }
        public Integer getForceIndex() { return forceIndex; }
        public void setForceIndex(Integer forceIndex) { this.forceIndex = forceIndex; }
    }

    /**
     * Evaluates a forced feature and returns the appropriate evaluation result
     * This method matches the PHP implementation's evaluate() method
     *
     * @param options The evaluation options containing type, featureKey, variableKey, context, logger, and datafileReader
     * @param feature The feature to evaluate
     * @param variableSchema The variable schema (can be null)
     * @return EvaluateForcedResult containing evaluation, force, and forceIndex
     */
    public static EvaluateForcedResult evaluate(EvaluateOptions options, Feature feature, VariableSchema variableSchema) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        Map<String, Object> context = options.getContext();
        Logger logger = options.getLogger();
        DatafileReader datafileReader = options.getDatafileReader();

        DatafileReader.ForceResult forceResult = datafileReader.getMatchedForce(feature, context);
        Force force = forceResult.getForce();
        Integer forceIndex = forceResult.getForceIndex();

        EvaluateForcedResult result = new EvaluateForcedResult();
        result.setForce(force);
        result.setForceIndex(forceIndex);

        if (force != null) {
            // flag
            if (Evaluation.TYPE_FLAG.equals(type) && force.getEnabled() != null) {
                Evaluation evaluation = new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_FORCED)
                    .forceIndex(forceIndex)
                    .force(forceToMap(force))
                    .enabled(force.getEnabled());

                result.setEvaluation(evaluation);

                logger.debug("forced enabled found", evaluationToMap(evaluation));

                return result;
            }

            // variation
            if (Evaluation.TYPE_VARIATION.equals(type) && force.getVariation() != null && feature.getVariations() != null) {
                Variation variation = null;
                for (Variation v : feature.getVariations()) {
                    if (force.getVariation().equals(v.getValue())) {
                        variation = v;
                        break;
                    }
                }

                if (variation != null) {
                    Evaluation evaluation = new Evaluation()
                        .type(type)
                        .featureKey(featureKey)
                        .reason(Evaluation.REASON_FORCED)
                        .forceIndex(forceIndex)
                        .force(forceToMap(force))
                        .variation(variation);

                    result.setEvaluation(evaluation);

                    logger.debug("forced variation found", evaluationToMap(evaluation));

                    return result;
                }
            }

            // variable
            // @NOTE: this implementation here deviated from PHP implementation. in PHP, it was partially delegated to EvaluateByBucketing
            if (variableKey != null) {
                Object variableValue = null;

                // First check if force has direct variables
                if (force.getVariables() != null && force.getVariables().containsKey(variableKey)) {
                    variableValue = force.getVariables().get(variableKey);
                }
                // If no direct variable, check if force has a variation with variable overrides
                else if (force.getVariation() != null && feature.getVariations() != null) {
                    // Find the forced variation
                    Variation forcedVariation = null;
                    for (Variation v : feature.getVariations()) {
                        if (force.getVariation().equals(v.getValue())) {
                            forcedVariation = v;
                            break;
                        }
                    }

                    if (forcedVariation != null) {
                        // Get base variable value from variation
                        if (forcedVariation.getVariables() != null && forcedVariation.getVariables().containsKey(variableKey)) {
                            variableValue = forcedVariation.getVariables().get(variableKey);
                        }

                        // Apply variable overrides if they exist
                        if (forcedVariation.getVariableOverrides() != null &&
                            forcedVariation.getVariableOverrides().containsKey(variableKey)) {

                            // Get variable overrides for this variable
                            List<VariableOverride> overrides = forcedVariation.getVariableOverrides().get(variableKey);

                            // Check each override to see if segments match
                            for (VariableOverride override : overrides) {
                                boolean matches = false;

                                // Check conditions
                                if (override.getConditions() != null) {
                                    matches = datafileReader.allConditionsAreMatched(override.getConditions(), context);
                                }
                                // Check segments
                                else if (override.getSegments() != null) {
                                    Object parsedSegments = datafileReader.parseSegmentsIfStringified(override.getSegments());
                                    matches = datafileReader.allSegmentsAreMatched(parsedSegments, context);
                                }

                                if (matches) {
                                    variableValue = override.getValue();
                                    break; // Use the first matching override
                                }
                            }
                        }
                    }
                }

                if (variableValue != null) {
                    Evaluation evaluation = new Evaluation()
                        .type(type)
                        .featureKey(featureKey)
                        .reason(Evaluation.REASON_FORCED)
                        .forceIndex(forceIndex)
                        .force(forceToMap(force))
                        .variableKey(variableKey)
                        .variableSchema(variableSchema)
                        .variableValue(variableValue);

                    result.setEvaluation(evaluation);

                    logger.debug("forced variable", evaluationToMap(evaluation));

                    return result;
                }
            }
        }

        return result;
    }

    /**
     * Legacy method for backward compatibility
     * @deprecated Use evaluate() instead
     */
    @Deprecated
    public static EvaluateForcedResult evaluateForced(EvaluateOptions options, Feature feature, VariableSchema variableSchema) {
        return evaluate(options, feature, variableSchema);
    }

    /**
     * Converts a Force object to a Map for storage in Evaluation
     *
     * @param force The Force object to convert
     * @return Map representation of the Force object
     */
    private static Map<String, Object> forceToMap(Force force) {
        Map<String, Object> forceMap = new HashMap<>();
        forceMap.put("conditions", force.getConditions());
        forceMap.put("segments", force.getSegments());
        forceMap.put("enabled", force.getEnabled());
        forceMap.put("variation", force.getVariation());
        forceMap.put("variables", force.getVariables());
        return forceMap;
    }

    /**
     * Converts an Evaluation object to a Map for logging
     *
     * @param evaluation The Evaluation object to convert
     * @return Map representation of the Evaluation object
     */
    private static Map<String, Object> evaluationToMap(Evaluation evaluation) {
        Map<String, Object> evaluationMap = new HashMap<>();
        evaluationMap.put("type", evaluation.getType());
        evaluationMap.put("featureKey", evaluation.getFeatureKey());
        evaluationMap.put("reason", evaluation.getReason());
        evaluationMap.put("forceIndex", evaluation.getForceIndex());
        evaluationMap.put("force", evaluation.getForce());
        evaluationMap.put("enabled", evaluation.getEnabled());
        evaluationMap.put("variation", evaluation.getVariation());
        evaluationMap.put("variableKey", evaluation.getVariableKey());
        evaluationMap.put("variableSchema", evaluation.getVariableSchema());
        evaluationMap.put("variableValue", evaluation.getVariableValue());
        return evaluationMap;
    }
}
