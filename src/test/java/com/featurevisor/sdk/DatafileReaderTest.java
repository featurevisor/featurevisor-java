package com.featurevisor.sdk;

import com.featurevisor.types.Allocation;
import com.featurevisor.types.Bucket;
import com.featurevisor.types.Condition;
import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Feature;
import com.featurevisor.types.Operator;
import com.featurevisor.types.Range;
import com.featurevisor.types.Segment;
import com.featurevisor.types.Traffic;
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

public class DatafileReaderTest {

    private Logger logger;

    @BeforeEach
    public void setUp() {
        logger = Logger.createLogger(new Logger.CreateLoggerOptions().level(Logger.LogLevel.WARN));
    }

    @Test
    public void testDatafileReaderIsClass() {
        // This test verifies that DatafileReader is a class
        assertNotNull(DatafileReader.class);
    }

    @Test
    public void testV2DatafileSchemaReturnsRequestedEntities() {
        // Create test datafile content
        DatafileContent datafileContent = new DatafileContent();
        datafileContent.setSchemaVersion("2");
        datafileContent.setRevision("1");

        // Create segments
        Map<String, Segment> segments = new HashMap<>();

        Segment netherlandsSegment = new Segment();
        netherlandsSegment.setKey("netherlands");
        List<Condition> netherlandsConditions = new ArrayList<>();
        Condition netherlandsCondition = new Condition();
        netherlandsCondition.setAttribute("country");
        netherlandsCondition.setOperator(Operator.EQUALS);
        netherlandsCondition.setValue("nl");
        netherlandsConditions.add(netherlandsCondition);
        netherlandsSegment.setConditions(netherlandsConditions);
        segments.put("netherlands", netherlandsSegment);

        Segment germanySegment = new Segment();
        germanySegment.setKey("germany");
        // Germany segment has stringified conditions
        germanySegment.setConditions("[{\"attribute\":\"country\",\"operator\":\"equals\",\"value\":\"de\"}]");
        segments.put("germany", germanySegment);

        datafileContent.setSegments(segments);

        // Create features
        Map<String, Feature> features = new HashMap<>();

        Feature testFeature = new Feature();
        testFeature.setKey("test");
        testFeature.setBucketBy(new Bucket("userId"));

        List<Variation> variations = new ArrayList<>();
        Variation controlVariation = new Variation();
        controlVariation.setValue("control");
        variations.add(controlVariation);

        Variation treatmentVariation = new Variation();
        treatmentVariation.setValue("treatment");
        Map<String, Object> variables = new HashMap<>();
        variables.put("showSidebar", true);
        treatmentVariation.setVariables(variables);
        variations.add(treatmentVariation);

        testFeature.setVariations(variations);

        List<Traffic> traffic = new ArrayList<>();
        Traffic trafficRule = new Traffic();
        trafficRule.setKey("1");
        trafficRule.setSegments("*");
        trafficRule.setPercentage(100000);

        List<Allocation> allocations = new ArrayList<>();
        Allocation controlAllocation = new Allocation();
        controlAllocation.setVariation("control");
        List<Integer> controlRange = new ArrayList<>();
        controlRange.add(0);
        controlRange.add(0);
        controlAllocation.setRange(new Range(controlRange));
        allocations.add(controlAllocation);

        Allocation treatmentAllocation = new Allocation();
        treatmentAllocation.setVariation("treatment");
        List<Integer> treatmentRange = new ArrayList<>();
        treatmentRange.add(0);
        treatmentRange.add(100000);
        treatmentAllocation.setRange(new Range(treatmentRange));
        allocations.add(treatmentAllocation);

        trafficRule.setAllocation(allocations);
        traffic.add(trafficRule);
        testFeature.setTraffic(traffic);

        features.put("test", testFeature);
        datafileContent.setFeatures(features);

        // Create DatafileReader
        DatafileReader reader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafileContent)
            .logger(logger));

        // Test basic getters
        assertEquals("1", reader.getRevision());
        assertEquals("2", reader.getSchemaVersion());

        // Test segment retrieval
        Segment netherlands = reader.getSegment("netherlands");
        assertNotNull(netherlands);
        assertEquals("netherlands", netherlands.getKey());

        Segment germany = reader.getSegment("germany");
        assertNotNull(germany);
        assertEquals("germany", germany.getKey());

        // Test that stringified conditions are parsed
        assertNotNull(germany.getConditions());

        Segment belgium = reader.getSegment("belgium");
        assertNull(belgium);

        // Test feature retrieval
        Feature test = reader.getFeature("test");
        assertNotNull(test);
        assertEquals("test", test.getKey());

        Feature test2 = reader.getFeature("test2");
        assertNull(test2);
    }

    @Test
    public void testSegmentMatching() {
        // Create datafile with segments
        DatafileContent datafileContent = new DatafileContent();
        datafileContent.setSchemaVersion("2");
        datafileContent.setRevision("1");
        datafileContent.setFeatures(new HashMap<>());

        Map<String, Segment> segments = new HashMap<>();

        // Mobile users segment
        Segment mobileUsers = new Segment();
        mobileUsers.setKey("mobileUsers");
        List<Condition> mobileConditions = new ArrayList<>();
        Condition mobileCondition = new Condition();
        mobileCondition.setAttribute("deviceType");
        mobileCondition.setOperator(Operator.EQUALS);
        mobileCondition.setValue("mobile");
        mobileConditions.add(mobileCondition);
        mobileUsers.setConditions(mobileConditions);
        segments.put("mobileUsers", mobileUsers);

        // Desktop users segment
        Segment desktopUsers = new Segment();
        desktopUsers.setKey("desktopUsers");
        List<Condition> desktopConditions = new ArrayList<>();
        Condition desktopCondition = new Condition();
        desktopCondition.setAttribute("deviceType");
        desktopCondition.setOperator(Operator.EQUALS);
        desktopCondition.setValue("desktop");
        desktopConditions.add(desktopCondition);
        desktopUsers.setConditions(desktopConditions);
        segments.put("desktopUsers", desktopUsers);

        // Netherlands segment
        Segment netherlands = new Segment();
        netherlands.setKey("netherlands");
        List<Condition> netherlandsConditions = new ArrayList<>();
        Condition netherlandsCondition = new Condition();
        netherlandsCondition.setAttribute("country");
        netherlandsCondition.setOperator(Operator.EQUALS);
        netherlandsCondition.setValue("nl");
        netherlandsConditions.add(netherlandsCondition);
        netherlands.setConditions(netherlandsConditions);
        segments.put("netherlands", netherlands);

        // Germany segment
        Segment germany = new Segment();
        germany.setKey("germany");
        List<Condition> germanyConditions = new ArrayList<>();
        Condition germanyCondition = new Condition();
        germanyCondition.setAttribute("country");
        germanyCondition.setOperator(Operator.EQUALS);
        germanyCondition.setValue("de");
        germanyConditions.add(germanyCondition);
        germany.setConditions(germanyConditions);
        segments.put("germany", germany);

        datafileContent.setSegments(segments);

        DatafileReader reader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafileContent)
            .logger(logger));

        // Test wildcard segments
        assertTrue(reader.allSegmentsAreMatched("*", new HashMap<>()));
        assertTrue(reader.allSegmentsAreMatched("*", Map.of("foo", "foo")));
        assertTrue(reader.allSegmentsAreMatched("*", Map.of("bar", "bar")));

        // Test single segment matching
        Map<String, Object> mobileContext = Map.of("deviceType", "mobile");
        assertTrue(reader.allSegmentsAreMatched("mobileUsers", mobileContext));

        Map<String, Object> desktopContext = Map.of("deviceType", "desktop");
        assertTrue(reader.allSegmentsAreMatched("desktopUsers", desktopContext));

        Map<String, Object> netherlandsContext = Map.of("country", "nl");
        assertTrue(reader.allSegmentsAreMatched("netherlands", netherlandsContext));

        Map<String, Object> germanyContext = Map.of("country", "de");
        assertTrue(reader.allSegmentsAreMatched("germany", germanyContext));

        // Test non-matching contexts
        assertFalse(reader.allSegmentsAreMatched("mobileUsers", new HashMap<>()));
        assertFalse(reader.allSegmentsAreMatched("mobileUsers", Map.of("deviceType", "desktop")));
        assertFalse(reader.allSegmentsAreMatched("netherlands", Map.of("country", "de")));

        // Test AND segments
        List<String> andSegments = List.of("mobileUsers", "netherlands");
        Map<String, Object> mobileNetherlandsContext = Map.of("deviceType", "mobile", "country", "nl");
        assertTrue(reader.allSegmentsAreMatched(andSegments, mobileNetherlandsContext));
        assertFalse(reader.allSegmentsAreMatched(andSegments, Map.of("deviceType", "mobile")));
        assertFalse(reader.allSegmentsAreMatched(andSegments, Map.of("country", "nl")));

        // Test OR segments
        Map<String, Object> orSegments = Map.of("or", List.of("mobileUsers", "desktopUsers"));
        Map<String, Object> mobileOrDesktopContext = Map.of("deviceType", "mobile");
        assertTrue(reader.allSegmentsAreMatched(orSegments, mobileOrDesktopContext));

        mobileOrDesktopContext = Map.of("deviceType", "desktop");
        assertTrue(reader.allSegmentsAreMatched(orSegments, mobileOrDesktopContext));

        mobileOrDesktopContext = Map.of("deviceType", "tablet");
        assertFalse(reader.allSegmentsAreMatched(orSegments, mobileOrDesktopContext));
    }

    @Test
    public void testFeatureOperations() {
        // Create datafile with features
        DatafileContent datafileContent = new DatafileContent();
        datafileContent.setSchemaVersion("2");
        datafileContent.setRevision("1");
        datafileContent.setSegments(new HashMap<>());

        Map<String, Feature> features = new HashMap<>();

        Feature testFeature = new Feature();
        testFeature.setKey("test");
        testFeature.setBucketBy(new Bucket("userId"));

        // Add variations
        List<Variation> variations = new ArrayList<>();
        Variation variation1 = new Variation();
        variation1.setValue("control");
        variations.add(variation1);

        Variation variation2 = new Variation();
        variation2.setValue("treatment");
        variations.add(variation2);

        testFeature.setVariations(variations);

        // Add variables schema
        Map<String, VariableSchema> variablesSchema = new HashMap<>();
        VariableSchema var1 = new VariableSchema();
        var1.setType(VariableType.STRING);
        var1.setDefaultValue("default");
        variablesSchema.put("var1", var1);

        VariableSchema var2 = new VariableSchema();
        var2.setType(VariableType.BOOLEAN);
        var2.setDefaultValue(true);
        variablesSchema.put("var2", var2);

        testFeature.setVariablesSchema(variablesSchema);

        features.put("test", testFeature);
        datafileContent.setFeatures(features);

        DatafileReader reader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafileContent)
            .logger(logger));

        // Test feature keys
        List<String> featureKeys = reader.getFeatureKeys();
        assertEquals(1, featureKeys.size());
        assertTrue(featureKeys.contains("test"));

        // Test feature retrieval
        Feature feature = reader.getFeature("test");
        assertNotNull(feature);
        assertEquals("test", feature.getKey());

        // Test variable keys
        List<String> variableKeys = reader.getVariableKeys("test");
        assertEquals(2, variableKeys.size());
        assertTrue(variableKeys.contains("var1"));
        assertTrue(variableKeys.contains("var2"));

        // Test variations
        assertTrue(reader.hasVariations("test"));
        assertFalse(reader.hasVariations("nonexistent"));
    }

    @Test
    public void testTrafficMatching() {
        // Create datafile with traffic rules
        DatafileContent datafileContent = new DatafileContent();
        datafileContent.setSchemaVersion("2");
        datafileContent.setRevision("1");

        // Create segments
        Map<String, Segment> segments = new HashMap<>();
        Segment netherlandsSegment = new Segment();
        netherlandsSegment.setKey("netherlands");
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("country");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("nl");
        conditions.add(condition);
        netherlandsSegment.setConditions(conditions);
        segments.put("netherlands", netherlandsSegment);
        datafileContent.setSegments(segments);

        // Create feature with traffic
        Map<String, Feature> features = new HashMap<>();
        Feature feature = new Feature();
        feature.setKey("test");
        feature.setBucketBy(new Bucket("userId"));

        List<Traffic> traffic = new ArrayList<>();
        Traffic trafficRule = new Traffic();
        trafficRule.setKey("1");
        trafficRule.setSegments("netherlands");
        trafficRule.setPercentage(100000);

        List<Allocation> allocations = new ArrayList<>();
        Allocation allocation = new Allocation();
        allocation.setVariation("control");
        List<Integer> range = new ArrayList<>();
        range.add(0);
        range.add(50000);
        allocation.setRange(new Range(range));
        allocations.add(allocation);

        Allocation allocation2 = new Allocation();
        allocation2.setVariation("treatment");
        List<Integer> range2 = new ArrayList<>();
        range2.add(50000);
        range2.add(100000);
        allocation2.setRange(new Range(range2));
        allocations.add(allocation2);

        trafficRule.setAllocation(allocations);
        traffic.add(trafficRule);
        feature.setTraffic(traffic);

        features.put("test", feature);
        datafileContent.setFeatures(features);

        DatafileReader reader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafileContent)
            .logger(logger));

        // Test traffic matching
        Map<String, Object> netherlandsContext = Map.of("country", "nl");
        Traffic matchedTraffic = reader.getMatchedTraffic(traffic, netherlandsContext);
        assertNotNull(matchedTraffic);
        assertEquals("1", matchedTraffic.getKey());

        Map<String, Object> germanyContext = Map.of("country", "de");
        Traffic unmatchedTraffic = reader.getMatchedTraffic(traffic, germanyContext);
        assertNull(unmatchedTraffic);

        // Test allocation matching
        Allocation matchedAllocation = reader.getMatchedAllocation(matchedTraffic, 25000);
        assertNotNull(matchedAllocation);
        assertEquals("control", matchedAllocation.getVariation());

        Allocation matchedAllocation2 = reader.getMatchedAllocation(matchedTraffic, 75000);
        assertNotNull(matchedAllocation2);
        assertEquals("treatment", matchedAllocation2.getVariation());

        Allocation unmatchedAllocation = reader.getMatchedAllocation(matchedTraffic, 150000);
        assertNull(unmatchedAllocation);
    }
}
