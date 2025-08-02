package com.featurevisor.sdk;

import com.featurevisor.types.Feature;
import com.featurevisor.types.VariableSchema;

/**
 * Evaluates disabled features and returns appropriate evaluation results
 * This class handles the logic for evaluating disabled features, variables, and variations
 */
public class EvaluateDisabled {

    /**
     * Evaluates a disabled feature and returns the appropriate evaluation result
     *
     * @param options The evaluation options containing type, featureKey, datafileReader, variableKey, and logger
     * @param flag The flag evaluation result
     * @return Evaluation result for disabled feature, or null if not disabled
     */
    public static Evaluation evaluateDisabled(EvaluateOptions options, Evaluation flag) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        DatafileReader datafileReader = options.getDatafileReader();
        String variableKey = options.getVariableKey();
        Logger logger = options.getLogger();

        if (!Evaluation.TYPE_FLAG.equals(type)) {
            if (flag != null && Boolean.FALSE.equals(flag.getEnabled())) {
                Evaluation evaluation = new Evaluation()
                    .type(type)
                    .featureKey(featureKey)
                    .reason(Evaluation.REASON_DISABLED);

                Feature feature = datafileReader.getFeature(featureKey);

                // serve variable default value if feature is disabled (if explicitly specified)
                if (Evaluation.TYPE_VARIABLE.equals(type)) {
                    if (feature != null && variableKey != null &&
                        feature.getVariablesSchema() != null &&
                        feature.getVariablesSchema().containsKey(variableKey)) {

                        VariableSchema variableSchema = feature.getVariablesSchema().get(variableKey);

                        if (variableSchema.getDisabledValue() != null) {
                            // disabledValue: <value>
                            evaluation = new Evaluation()
                                .type(type)
                                .featureKey(featureKey)
                                .reason(Evaluation.REASON_VARIABLE_DISABLED)
                                .variableKey(variableKey)
                                .variableValue(variableSchema.getDisabledValue())
                                .variableSchema(variableSchema)
                                .enabled(false);
                        } else if (Boolean.TRUE.equals(variableSchema.getUseDefaultWhenDisabled())) {
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

                logger.debug("feature is disabled", null);

                return evaluation;
            }
        }

        return null;
    }
}
