package com.featurevisor.sdk;

import com.featurevisor.sdk.Feature;
import com.featurevisor.sdk.VariableSchema;

/**
 * Evaluates disabled features and returns appropriate evaluation results
 * This class handles the logic for evaluating disabled features, variables, and variations
 */
final class EvaluateDisabled {

    /**
     * Evaluates a disabled feature and returns the appropriate evaluation result
     *
     * @param options The evaluation options containing type, featureKey, evaluationData, variableKey, and diagnostics
     * @param flag The flag evaluation result
     * @return Evaluation result for disabled feature, or null if not disabled
     */
    public static Evaluation evaluateDisabled(EvaluateOptions options, Evaluation flag) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        InstanceEvaluationDataProvider evaluationData = options.getInstanceEvaluationDataProvider();
        String variableKey = options.getVariableKey();
        DiagnosticReporter diagnostics = options.getDiagnostics();

        if (!Evaluation.TYPE_FLAG.equals(type)) {
            if (flag != null && Boolean.FALSE.equals(flag.getEnabled())) {
                Evaluation evaluation = new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_DISABLED);

                Feature feature = evaluationData.getFeature(featureKey);

                // serve variable default value if feature is disabled (if explicitly specified)
                if (Evaluation.TYPE_VARIABLE.equals(type)) {
                    if (feature != null && variableKey != null &&
                        feature.getVariablesSchema() != null &&
                        feature.getVariablesSchema().containsKey(variableKey)) {

                        VariableSchema variableSchema = feature.getVariablesSchema().get(variableKey);

                        if (variableSchema.hasDisabledValue()) {
                            // disabledValue: <value>
                            evaluation = new Evaluation()
                                .type(type)
                                .featureKey(featureKey)
                                .reason(Evaluation.REASON_VARIABLE_DISABLED)
                                .variableKey(variableKey)
                                .variableValue(variableSchema.getDisabledValue())
                                .variableSchema(variableSchema)
                                .enabled(false);
                        } else if (Boolean.TRUE.equals(variableSchema.getUseDefaultWhenDisabled()) && variableSchema.hasDefaultValue()) {
                            // useDefaultWhenDisabled: true
                            evaluation = new Evaluation()
                                .type(type)
                                .featureKey(featureKey)
                                .reason(Evaluation.REASON_VARIABLE_DEFAULT)
                                .variableKey(variableKey)
                                .variableValue(variableSchema.getDefaultValue())
                                .variableSchema(variableSchema)
                                .enabled(false);
                        }
                    }
                }

                // serve disabled variation value if feature is disabled (if explicitly specified)
                if (Evaluation.TYPE_VARIATION.equals(type) && feature != null &&
                    feature.getDisabledVariationValue() != null) {
                    evaluation = new Evaluation()
                        .type(type)
                        .featureKey(featureKey)
                        .reason(Evaluation.REASON_VARIATION_DISABLED)
                        .variationValue(feature.getDisabledVariationValue())
                        .enabled(false);
                }

                diagnostics.debug("feature is disabled", null);

                return evaluation;
            }
        }

        return null;
    }
}
