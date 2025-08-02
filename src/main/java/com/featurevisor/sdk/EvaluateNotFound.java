package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;

/**
 * Evaluates not found scenarios and returns appropriate evaluation results
 * This class handles the logic for evaluating when features or variables are not found
 */
public class EvaluateNotFound {

    /**
     * Result of not found evaluation containing evaluation, feature, and variableSchema
     */
    public static class EvaluateNotFoundResult {
        private Evaluation evaluation;
        private Feature feature;
        private VariableSchema variableSchema;

        public EvaluateNotFoundResult() {}

        public EvaluateNotFoundResult(Evaluation evaluation, Feature feature, VariableSchema variableSchema) {
            this.evaluation = evaluation;
            this.feature = feature;
            this.variableSchema = variableSchema;
        }

        // Getters and Setters
        public Evaluation getEvaluation() { return evaluation; }
        public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }
        public Feature getFeature() { return feature; }
        public void setFeature(Feature feature) { this.feature = feature; }
        public VariableSchema getVariableSchema() { return variableSchema; }
        public void setVariableSchema(VariableSchema variableSchema) { this.variableSchema = variableSchema; }
    }

    /**
     * Evaluates not found scenarios and returns the appropriate evaluation result
     *
     * @param options The evaluation options containing type, featureKey, variableKey, logger, and datafileReader
     * @return EvaluateNotFoundResult containing evaluation, feature, and variableSchema
     */
    public static EvaluateNotFoundResult evaluateNotFound(EvaluateOptions options) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        Logger logger = options.getLogger();
        DatafileReader datafileReader = options.getDatafileReader();

        EvaluateNotFoundResult result = new EvaluateNotFoundResult();

        Feature feature = datafileReader.getFeature(featureKey);

        // feature: not found
        if (feature == null) {
            Evaluation evaluation = new Evaluation()
                .type(type)
                .featureKey(featureKey)
                .reason(Evaluation.REASON_FEATURE_NOT_FOUND);

            result.setEvaluation(evaluation);

            logger.warn("feature not found", null);

            return result;
        }

        result.setFeature(feature);

        // feature: deprecated
        if (Evaluation.TYPE_FLAG.equals(type) && Boolean.TRUE.equals(feature.getDeprecated())) {
            logger.warn("feature is deprecated", null);
        }

        // variableSchema
        VariableSchema variableSchema = null;

        if (variableKey != null) {
            if (feature.getVariablesSchema() != null && feature.getVariablesSchema().containsKey(variableKey)) {
                variableSchema = feature.getVariablesSchema().get(variableKey);
            }

            // variable schema not found
            if (variableSchema == null) {
                Evaluation evaluation = new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_VARIABLE_NOT_FOUND)
                    .variableKey(variableKey);

                result.setEvaluation(evaluation);

                logger.warn("variable schema not found", null);

                return result;
            }

            result.setVariableSchema(variableSchema);

            if (Boolean.TRUE.equals(variableSchema.getDeprecated())) {
                logger.warn("variable is deprecated", null);
            }
        }

        // variation: no variations
        if (Evaluation.TYPE_VARIATION.equals(type) &&
            (feature.getVariations() == null || feature.getVariations().isEmpty())) {
            Evaluation evaluation = new Evaluation()
                .type(type)
                .featureKey(featureKey)
                .reason(Evaluation.REASON_NO_VARIATIONS);

            result.setEvaluation(evaluation);

            logger.warn("no variations", null);

            return result;
        }

        return result;
    }
}
