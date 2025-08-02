package com.featurevisor.sdk;

import com.featurevisor.types.DatafileContent;
import com.featurevisor.types.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class DatafileReaderComprehensiveTest {

    private DatafileReader datafileReader;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = new Logger();

        // Create a comprehensive datafile content similar to the TypeScript tests
        Map<String, Object> datafileContent = new HashMap<>();
        datafileContent.put("schemaVersion", "2");
        datafileContent.put("revision", "1");
        datafileContent.put("features", new HashMap<>());

        Map<String, Object> segments = new HashMap<>();

        // Device type segments
        Map<String, Object> mobileUsers = new HashMap<>();
        mobileUsers.put("key", "mobileUsers");
        List<Map<String, Object>> mobileConditions = new ArrayList<>();
        Map<String, Object> mobileCondition = new HashMap<>();
        mobileCondition.put("attribute", "deviceType");
        mobileCondition.put("operator", "equals");
        mobileCondition.put("value", "mobile");
        mobileConditions.add(mobileCondition);
        mobileUsers.put("conditions", mobileConditions);
        segments.put("mobileUsers", mobileUsers);

        Map<String, Object> desktopUsers = new HashMap<>();
        desktopUsers.put("key", "desktopUsers");
        List<Map<String, Object>> desktopConditions = new ArrayList<>();
        Map<String, Object> desktopCondition = new HashMap<>();
        desktopCondition.put("attribute", "deviceType");
        desktopCondition.put("operator", "equals");
        desktopCondition.put("value", "desktop");
        desktopConditions.add(desktopCondition);
        desktopUsers.put("conditions", desktopConditions);
        segments.put("desktopUsers", desktopUsers);

        // Country segments
        Map<String, Object> netherlands = new HashMap<>();
        netherlands.put("key", "netherlands");
        List<Map<String, Object>> nlConditions = new ArrayList<>();
        Map<String, Object> nlCondition = new HashMap<>();
        nlCondition.put("attribute", "country");
        nlCondition.put("operator", "equals");
        nlCondition.put("value", "nl");
        nlConditions.add(nlCondition);
        netherlands.put("conditions", nlConditions);
        segments.put("netherlands", netherlands);

        Map<String, Object> germany = new HashMap<>();
        germany.put("key", "germany");
        List<Map<String, Object>> deConditions = new ArrayList<>();
        Map<String, Object> deCondition = new HashMap<>();
        deCondition.put("attribute", "country");
        deCondition.put("operator", "equals");
        deCondition.put("value", "de");
        deConditions.add(deCondition);
        germany.put("conditions", deConditions);
        segments.put("germany", germany);

        // Version segment
        Map<String, Object> version55 = new HashMap<>();
        version55.put("key", "version_5.5");
        List<Map<String, Object>> versionConditions = new ArrayList<>();
        Map<String, Object> versionCondition = new HashMap<>();
        versionCondition.put("or", Arrays.asList(
            Map.of("attribute", "version", "operator", "equals", "value", "5.5"),
            Map.of("attribute", "version", "operator", "equals", "value", 5.5)
        ));
        versionConditions.add(versionCondition);
        version55.put("conditions", versionConditions);
        segments.put("version_5.5", version55);

        datafileContent.put("segments", segments);

        // Convert the segments map to the proper type
        Map<String, Segment> segmentMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : segments.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> segmentData = (Map<String, Object>) entry.getValue();
            Segment segment = new Segment();
            segment.setKey((String) segmentData.get("key"));
            segment.setConditions(segmentData.get("conditions"));
            segmentMap.put(entry.getKey(), segment);
        }

        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2");
        datafile.setRevision("1");
        datafile.setSegments(segmentMap);
        datafile.setFeatures(new HashMap<>());

        datafileReader = new DatafileReader(new DatafileReader.DatafileReaderOptions()
            .datafile(datafile)
            .logger(logger));
    }

    @Test
    void testMatchEveryone() {
        // Test "*" segments - should match everyone
        assertTrue(datafileReader.allSegmentsAreMatched("*", new HashMap<>()));
        assertTrue(datafileReader.allSegmentsAreMatched("*", Map.of("foo", "foo")));
        assertTrue(datafileReader.allSegmentsAreMatched("*", Map.of("bar", "bar")));
    }

    @Test
    void testMatchDutchMobileUsers() {
        // Test ["mobileUsers", "netherlands"] - should match Dutch mobile users
        List<String> segments = Arrays.asList("mobileUsers", "netherlands");

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile", "browser", "chrome")));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "mobile")));
    }

    @Test
    void testMatchDutchMobileUsersWithAnd() {
        // Test {"and": ["mobileUsers", "netherlands"]} - should match Dutch mobile users
        Map<String, Object> segments = new HashMap<>();
        segments.put("and", Arrays.asList("mobileUsers", "netherlands"));

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile", "browser", "chrome")));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "mobile")));
    }

    @Test
    void testMatchDutchMobileOrDesktopUsers() {
        // Test ["netherlands", {"or": ["mobileUsers", "desktopUsers"]}] - should match Dutch mobile or desktop users
        List<Object> segments = Arrays.asList("netherlands",
            Map.of("or", Arrays.asList("mobileUsers", "desktopUsers")));

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile", "browser", "chrome")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "desktop")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "desktop", "browser", "chrome")));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "mobile")));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "desktop")));
    }

    @Test
    void testMatchGermanMobileUsers() {
        // Test [{"and": ["mobileUsers", "germany"]}] - should match German mobile users
        List<Map<String, Object>> segments = Arrays.asList(
            Map.of("and", Arrays.asList("mobileUsers", "germany"))
        );

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "mobile")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "mobile", "browser", "chrome")));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "mobile")));
    }

    @Test
    void testMatchGermanNonMobileUsers() {
        // Test [{"and": ["germany", {"not": ["mobileUsers"]}]}] - should match German non-mobile users
        List<Map<String, Object>> segments = Arrays.asList(
            Map.of("and", Arrays.asList("germany", Map.of("not", Arrays.asList("mobileUsers"))))
        );

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "desktop")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "de", "deviceType", "desktop", "browser", "chrome")));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertFalse(datafileReader.allSegmentsAreMatched(segments,
            Map.of("country", "nl", "deviceType", "desktop")));
    }

    @Test
    void testMatchNotVersion55() {
        // Test [{"not": ["version_5.5"]}] - should match everything except version 5.5
        List<Map<String, Object>> segments = Arrays.asList(
            Map.of("not", Arrays.asList("version_5.5"))
        );

        // Should match
        assertTrue(datafileReader.allSegmentsAreMatched(segments, new HashMap<>()));
        assertTrue(datafileReader.allSegmentsAreMatched(segments, Map.of("version", "5.6")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments, Map.of("version", 5.6)));
        assertTrue(datafileReader.allSegmentsAreMatched(segments, Map.of("version", "5.7")));
        assertTrue(datafileReader.allSegmentsAreMatched(segments, Map.of("version", 5.7)));

        // Should not match
        assertFalse(datafileReader.allSegmentsAreMatched(segments, Map.of("version", "5.5")));
        assertFalse(datafileReader.allSegmentsAreMatched(segments, Map.of("version", 5.5)));
    }

    @Test
    void testConditionsWithWildcard() {
        // Test "*" conditions - should match everything
        assertTrue(datafileReader.allConditionsAreMatched("*", new HashMap<>()));
        assertTrue(datafileReader.allConditionsAreMatched("*", Map.of("browser_type", "chrome")));

        // Test non-wildcard string conditions - should not match
        assertFalse(datafileReader.allConditionsAreMatched("blah", Map.of("browser_type", "chrome")));
    }

    @Test
    void testSimpleConditionEquals() {
        // Test simple equals condition
        Map<String, Object> condition = Map.of("attribute", "browser_type", "operator", "equals", "value", "chrome");

        // Should match
        assertTrue(datafileReader.allConditionsAreMatched(condition, Map.of("browser_type", "chrome")));

        // Should not match
        assertFalse(datafileReader.allConditionsAreMatched(condition, Map.of("browser_type", "firefox")));
    }

    @Test
    void testConditionWithDotSeparatedPath() {
        // Test condition with dot-separated path
        Map<String, Object> condition = Map.of("attribute", "browser.type", "operator", "equals", "value", "chrome");

        // Should match
        assertTrue(datafileReader.allConditionsAreMatched(condition,
            Map.of("browser", Map.of("type", "chrome"))));

        // Should not match
        assertFalse(datafileReader.allConditionsAreMatched(condition,
            Map.of("browser", Map.of("type", "firefox"))));
        assertFalse(datafileReader.allConditionsAreMatched(condition,
            Map.of("browser", Map.of("blah", "firefox"))));
        assertFalse(datafileReader.allConditionsAreMatched(condition, Map.of("browser", "firefox")));
    }
}
