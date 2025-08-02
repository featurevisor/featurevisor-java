package com.featurevisor.sdk;

import java.util.Map;

/**
 * Evaluates sticky features and returns appropriate evaluation results
 * This class handles the logic for evaluating sticky features, variables, and variations
 */
public class EvaluateSticky {

    /**
     * Evaluates sticky scenarios and returns the appropriate evaluation result
     *
     * @param options The evaluation options containing type, featureKey, variableKey, sticky, and logger
     * @return Evaluation if sticky data is found and valid, null otherwise
     */
    public static Evaluation evaluateSticky(EvaluateOptions options) {
        String type = options.getType();
        String featureKey = options.getFeatureKey();
        String variableKey = options.getVariableKey();
        Map<String, Object> sticky = options.getSticky();
        Logger logger = options.getLogger();

        if (sticky != null && sticky.containsKey(featureKey)) {
            Object stickyData = sticky.get(featureKey);

            if (stickyData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stickyMap = (Map<String, Object>) stickyData;
                Evaluation evaluation;

                // flag
                if (Evaluation.TYPE_FLAG.equals(type) && stickyMap.containsKey("enabled")) {
                    evaluation = new Evaluation()
                        .type(type)
                        .featureKey(featureKey)
                        .reason(Evaluation.REASON_STICKY)
                        .sticky(stickyMap)
                        .enabled((Boolean) stickyMap.get("enabled"));

                    logger.debug("using sticky enabled", null);

                    return evaluation;
                }

                // variation
                if (Evaluation.TYPE_VARIATION.equals(type) && stickyMap.containsKey("variation")) {
                    Object variationValue = stickyMap.get("variation");

                    if (variationValue != null) {
                        evaluation = new Evaluation()
                            .type(type)
                            .featureKey(featureKey)
                            .reason(Evaluation.REASON_STICKY)
                            .variationValue(variationValue.toString());

                        logger.debug("using sticky variation", null);

                        return evaluation;
                    }
                }

                // variable
                if (variableKey != null && stickyMap.containsKey("variables")) {
                    Object variablesObj = stickyMap.get("variables");

                    if (variablesObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> variables = (Map<String, Object>) variablesObj;

                        if (variables.containsKey(variableKey)) {
                            Object result = variables.get(variableKey);

                            if (result != null) {
                                evaluation = new Evaluation()
                                    .type(type)
                                    .featureKey(featureKey)
                                    .reason(Evaluation.REASON_STICKY)
                                    .variableKey(variableKey)
                                    .variableValue(result);

                                logger.debug("using sticky variable", null);

                                return evaluation;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }
}
