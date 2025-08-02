package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

public class EvaluateStickyTest {

    private Logger logger;
    private Map<String, Object> sticky;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));
        sticky = new HashMap<>();
    }

    @Test
    public void testEvaluateStickyWithNoStickyData() {
        // Should return null when no sticky data is provided
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithFeatureNotFound() {
        // Should return null when feature is not in sticky data
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("enabled", true);
        sticky.put("other-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithFlagType() {
        // Should return evaluation when sticky enabled flag is found
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("enabled", true);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_FLAG, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertNotNull(result.getSticky());
        assertTrue(result.getEnabled());
    }

    @Test
    public void testEvaluateStickyWithFlagTypeDisabled() {
        // Should return evaluation when sticky disabled flag is found
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("enabled", false);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_FLAG, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertNotNull(result.getSticky());
        assertFalse(result.getEnabled());
    }

    @Test
    public void testEvaluateStickyWithFlagTypeNoEnabled() {
        // Should return null when flag type but no enabled field
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variation", "control");
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariationType() {
        // Should return evaluation when sticky variation is found
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variation", "treatment");
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIATION, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertEquals("treatment", result.getVariationValue());
    }

    @Test
    public void testEvaluateStickyWithVariationTypeNull() {
        // Should return null when variation value is null
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variation", null);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariationTypeNoVariation() {
        // Should return null when variation type but no variation field
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("enabled", true);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariableType() {
        // Should return evaluation when sticky variable is found
        Map<String, Object> variables = new HashMap<>();
        variables.put("test-variable", "test-value");

        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", variables);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIABLE, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertEquals("test-variable", result.getVariableKey());
        assertEquals("test-value", result.getVariableValue());
    }

    @Test
    public void testEvaluateStickyWithVariableTypeNullValue() {
        // Should return null when variable value is null
        Map<String, Object> variables = new HashMap<>();
        variables.put("test-variable", null);

        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", variables);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariableTypeNoVariableKey() {
        // Should return null when no variable key is provided
        Map<String, Object> variables = new HashMap<>();
        variables.put("test-variable", "test-value");

        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", variables);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariableTypeNoVariables() {
        // Should return null when no variables field
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("enabled", true);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithVariableTypeNoVariableFound() {
        // Should return null when variable key not found in variables
        Map<String, Object> variables = new HashMap<>();
        variables.put("other-variable", "other-value");

        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", variables);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithNonMapStickyData() {
        // Should return null when sticky data is not a Map
        sticky.put("test-feature", "not-a-map");

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithNonMapVariables() {
        // Should return null when variables is not a Map
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", "not-a-map");
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNull(result);
    }

    @Test
    public void testEvaluateStickyWithComplexVariableValue() {
        // Should handle complex variable values (not just strings)
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> complexValue = new HashMap<>();
        complexValue.put("nested", "value");
        complexValue.put("number", 42);
        variables.put("test-variable", complexValue);

        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variables", variables);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIABLE, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertEquals("test-variable", result.getVariableKey());
        assertNotNull(result.getVariableValue());
        assertTrue(result.getVariableValue() instanceof Map);
    }

    @Test
    public void testEvaluateStickyWithNumericVariationValue() {
        // Should handle numeric variation values (converted to string)
        Map<String, Object> featureSticky = new HashMap<>();
        featureSticky.put("variation", 1);
        sticky.put("test-feature", featureSticky);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .sticky(sticky)
            .logger(logger);

        Evaluation result = EvaluateSticky.evaluateSticky(options);

        assertNotNull(result);
        assertEquals(Evaluation.TYPE_VARIATION, result.getType());
        assertEquals("test-feature", result.getFeatureKey());
        assertEquals(Evaluation.REASON_STICKY, result.getReason());
        assertEquals("1", result.getVariationValue());
    }
}
