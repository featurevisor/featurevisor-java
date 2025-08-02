package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Variation;
import com.featurevisor.types.VariableSchema;
import com.featurevisor.types.VariableType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class EvaluateNotFoundTest {

    private Logger logger;
    private DatafileReader datafileReader;
    private Feature feature;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));

        // Create a test feature with variations
        feature = new Feature("test-feature");

        List<Variation> variations = new ArrayList<>();
        Variation variation1 = new Variation("control");
        variation1.setDescription("Control variation");
        variation1.setWeight(50);
        variations.add(variation1);

        Variation variation2 = new Variation("treatment");
        variation2.setDescription("Treatment variation");
        variation2.setWeight(50);
        variations.add(variation2);

        feature.setVariations(variations);

        // Create variable schema
        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        VariableSchema variableSchema = new VariableSchema();
        variableSchema.setKey("test-variable");
        variableSchema.setType(VariableType.STRING);
        variableSchema.setDefaultValue("default-value");
        variablesSchema.put("test-variable", variableSchema);
        feature.setVariablesSchema(variablesSchema);

        // Create datafile with the feature
        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2.0");
        datafile.setRevision("1");
        datafile.setSegments(new HashMap<>());

        Map<String, Feature> features = new HashMap<>();
        features.put("test-feature", feature);
        datafile.setFeatures(features);

        // Create DatafileReader with the populated datafile
        datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafile)
            .logger(logger));
    }

    @Test
    public void testEvaluateNotFoundWithFeatureNotFound() {
        // Should return feature not found evaluation when feature doesn't exist
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("non-existent-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_FLAG, result.getEvaluation().getType());
        assertEquals("non-existent-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_FEATURE_NOT_FOUND, result.getEvaluation().getReason());
        assertNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithFeatureFound() {
        // Should return result with feature when feature exists
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertEquals("test-feature", result.getFeature().getKey());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithDeprecatedFeature() {
        // Should log warning when feature is deprecated
        feature.setDeprecated(true);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_FLAG)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertTrue(result.getFeature().getDeprecated());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithVariableNotFound() {
        // Should return variable not found evaluation when variable doesn't exist
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("non-existent-variable")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIABLE, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_VARIABLE_NOT_FOUND, result.getEvaluation().getReason());
        assertEquals("non-existent-variable", result.getEvaluation().getVariableKey());
        assertNotNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithVariableFound() {
        // Should return result with variable schema when variable exists
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertNotNull(result.getVariableSchema());
        assertEquals("test-variable", result.getVariableSchema().getKey());
    }

    @Test
    public void testEvaluateNotFoundWithDeprecatedVariable() {
        // Should log warning when variable is deprecated
        VariableSchema variableSchema = feature.getVariablesSchema().get("test-variable");
        variableSchema.setDeprecated(true);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertNotNull(result.getVariableSchema());
        assertTrue(result.getVariableSchema().getDeprecated());
    }

    @Test
    public void testEvaluateNotFoundWithNoVariations() {
        // Should return no variations evaluation when feature has no variations
        feature.setVariations(null);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIATION, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_NO_VARIATIONS, result.getEvaluation().getReason());
        assertNotNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithEmptyVariations() {
        // Should return no variations evaluation when feature has empty variations list
        feature.setVariations(new ArrayList<>());

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIATION, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_NO_VARIATIONS, result.getEvaluation().getReason());
        assertNotNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithVariationsFound() {
        // Should return result with feature when variations exist
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIATION)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertNotNull(result.getFeature().getVariations());
        assertEquals(2, result.getFeature().getVariations().size());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithNoVariableKey() {
        // Should return result with feature when no variable key is provided
        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNull(result.getEvaluation());
        assertNotNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }

    @Test
    public void testEvaluateNotFoundWithNoVariablesSchema() {
        // Should return variable not found when feature has no variables schema
        feature.setVariablesSchema(null);

        EvaluateOptions options = new EvaluateOptions()
            .type(Evaluation.TYPE_VARIABLE)
            .featureKey("test-feature")
            .variableKey("test-variable")
            .datafileReader(datafileReader)
            .logger(logger);

        EvaluateNotFound.EvaluateNotFoundResult result = EvaluateNotFound.evaluateNotFound(options);

        assertNotNull(result);
        assertNotNull(result.getEvaluation());
        assertEquals(Evaluation.TYPE_VARIABLE, result.getEvaluation().getType());
        assertEquals("test-feature", result.getEvaluation().getFeatureKey());
        assertEquals(Evaluation.REASON_VARIABLE_NOT_FOUND, result.getEvaluation().getReason());
        assertEquals("test-variable", result.getEvaluation().getVariableKey());
        assertNotNull(result.getFeature());
        assertNull(result.getVariableSchema());
    }


}
