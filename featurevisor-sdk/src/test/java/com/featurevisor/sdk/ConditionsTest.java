package com.featurevisor.sdk;

import com.featurevisor.sdk.Condition;
import com.featurevisor.sdk.DatafileContent;
import com.featurevisor.sdk.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class ConditionsTest {

    private DiagnosticReporter diagnostics;
    private InstanceEvaluationDataProvider evaluationData;

    @BeforeEach
    public void setUp() {
        diagnostics = DiagnosticReporter.createDiagnosticReporter(new DiagnosticReporter.DiagnosticReporterOptions().level(FeaturevisorLogLevel.WARN));

        DatafileContent datafile = new DatafileContent();
        datafile.setSchemaVersion("2.0");
        datafile.setRevision("1");
        datafile.setSegments(new HashMap<>());
        datafile.setFeatures(new HashMap<>());

        evaluationData = new InstanceEvaluationDataProvider(new InstanceEvaluationDataProvider.InstanceEvaluationDataProviderOptions()
            .datafile(datafile)
            .diagnostics(diagnostics));
    }

    @Test
    public void testConditionsIsFunction() {
        // Test that the method exists and can be called
        assertTrue(evaluationData.allConditionsAreMatched("*", new HashMap<>()));
    }

    @Test
    public void testMatchAllViaWildcard() {
        // match
        String conditions = "*";
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));

        // not match
        String conditions2 = "blah";
        assertFalse(evaluationData.allConditionsAreMatched(conditions2, Map.of("browser_type", "chrome")));
    }

    @Test
    public void testJavaScriptPrimitiveAndPresenceSemantics() {
        Condition equalsNumber = new Condition();
        equalsNumber.setAttribute("value");
        equalsNumber.setOperator(Operator.EQUALS);
        equalsNumber.setValue(1);

        assertFalse(evaluationData.allConditionsAreMatched(equalsNumber, Map.of("value", "1")));
        assertFalse(evaluationData.allConditionsAreMatched(equalsNumber, Map.of("value", true)));
        assertTrue(evaluationData.allConditionsAreMatched(equalsNumber, Map.of("value", 1.0)));
        assertTrue(evaluationData.allConditionsAreMatched(
            condition("missing", Operator.NOT_EQUALS, "value"), Map.of()));
        assertTrue(evaluationData.allConditionsAreMatched(
            condition("missing", Operator.NOT_EQUALS, null), Map.of()));

        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put("value", null);
        assertTrue(evaluationData.allConditionsAreMatched(condition("value", Operator.EXISTS, null), explicitNull));
        assertFalse(evaluationData.allConditionsAreMatched(condition("value", Operator.NOT_EXISTS, null), explicitNull));
        assertFalse(evaluationData.allConditionsAreMatched(condition("missing", Operator.IN, Arrays.asList((Object) null)), Map.of()));
    }

    @Test
    public void testIncludesAllPrimitivesAndRegexSearchSemantics() {
        Map<String, Object> context = new HashMap<>();
        context.put("values", Arrays.asList(1, true, null));
        context.put("text", "prefix-value-suffix");

        assertTrue(evaluationData.allConditionsAreMatched(condition("values", Operator.INCLUDES, 1.0), context));
        assertTrue(evaluationData.allConditionsAreMatched(condition("values", Operator.INCLUDES, true), context));
        assertTrue(evaluationData.allConditionsAreMatched(condition("values", Operator.INCLUDES, null), context));
        assertTrue(evaluationData.allConditionsAreMatched(condition("text", Operator.MATCHES, "value"), context));
        assertFalse(evaluationData.allConditionsAreMatched(condition("text", Operator.NOT_INCLUDES, "value"), context));
    }

    private Condition condition(String attribute, Operator operator, Object value) {
        Condition condition = new Condition();
        condition.setAttribute(attribute);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    @Test
    public void testOperatorEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));
    }

    @Test
    public void testOperatorEqualsWithDotSeparatedPath() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser.type");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> browser = new HashMap<>();
        browser.put("type", "chrome");
        context.put("browser", browser);
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        // not match
        browser.put("type", "firefox");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));

        browser.put("blah", "firefox");
        browser.remove("type");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));

        context.clear();
        context.put("browser", "firefox");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testOperatorNotEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.NOT_EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
    }

    @Test
    public void testOperatorExists() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.EXISTS);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("not_browser_type", "chrome")));
    }

    @Test
    public void testOperatorExistsWithDotSeparatedPath() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser.name");
        condition.setOperator(Operator.EXISTS);
        conditions.add(condition);

        // match
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> browser = new HashMap<>();
        browser.put("name", "chrome");
        context.put("browser", browser);
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        // not match
        context.clear();
        context.put("browser", "chrome");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));

        browser.clear();
        browser.put("version", "1.2.3");
        context.put("browser", browser);
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));

        context.clear();
        context.put("version", "1.2.3");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testOperatorNotExists() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.NOT_EXISTS);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("not_name", "Hello World")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("not_name", "Hello Universe")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));
    }

    @Test
    public void testOperatorNotExistsWithDotSeparatedPath() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser.name");
        condition.setOperator(Operator.NOT_EXISTS);
        conditions.add(condition);

        // match
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> browser = new HashMap<>();
        browser.put("not_name", "Hello World");
        context.put("browser", browser);
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        context.clear();
        context.put("not_name", "Hello Universe");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        // not match
        browser.clear();
        browser.put("name", "Chrome");
        context.put("browser", browser);
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testOperatorEndsWith() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.ENDS_WITH);
        condition.setValue("World");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello World")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi Universe")));
    }

    @Test
    public void testOperatorIncludes() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("permissions");
        condition.setOperator(Operator.INCLUDES);
        condition.setValue("write");
        conditions.add(condition);

        // match
        List<String> permissions = new ArrayList<>();
        permissions.add("read");
        permissions.add("write");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("permissions", permissions)));

        // not match
        permissions.clear();
        permissions.add("read");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("permissions", permissions)));
    }

    @Test
    public void testOperatorNotIncludes() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("permissions");
        condition.setOperator(Operator.NOT_INCLUDES);
        condition.setValue("write");
        conditions.add(condition);

        // match
        List<String> permissions = new ArrayList<>();
        permissions.add("read");
        permissions.add("admin");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("permissions", permissions)));

        // not match
        permissions.add("write");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("permissions", permissions)));
    }

    @Test
    public void testOperatorContains() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.CONTAINS);
        condition.setValue("Hello");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello World")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Yo! Hello!")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));
    }

    @Test
    public void testOperatorNotContains() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.NOT_CONTAINS);
        condition.setValue("Hello");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello World")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Yo! Hello!")));
    }

    @Test
    public void testOperatorMatches() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.MATCHES);
        condition.setValue("^[a-zA-Z]{2,}$");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Helloooooo")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello World")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hell123")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "123")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", 123)));
    }

    @Test
    public void testOperatorMatchesWithRegexFlags() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.MATCHES);
        condition.setValue("^[a-zA-Z]{2,}$");
        condition.setRegexFlags("i");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Helloooooo")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello World")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hell123")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "123")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", 123)));
    }

    @Test
    public void testOperatorNotMatches() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.NOT_MATCHES);
        condition.setValue("^[a-zA-Z]{2,}$");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "123")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hellooooooo")));
    }

    @Test
    public void testOperatorNotMatchesWithRegexFlags() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("name");
        condition.setOperator(Operator.NOT_MATCHES);
        condition.setValue("^[a-zA-Z]{2,}$");
        condition.setRegexFlags("i");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hi World")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "123")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hello")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("name", "Hellooooooo")));
    }

    @Test
    public void testOperatorIn() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.IN);
        List<String> values = new ArrayList<>();
        values.add("chrome");
        values.add("firefox");
        condition.setValue(values);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "edge")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "safari")));
    }

    @Test
    public void testOperatorNotIn() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.NOT_IN);
        List<String> values = new ArrayList<>();
        values.add("chrome");
        values.add("firefox");
        condition.setValue(values);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "edge")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "safari")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));
    }

    @Test
    public void testOperatorGreaterThan() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("age");
        condition.setOperator(Operator.GREATER_THAN);
        condition.setValue(18);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 19)));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 17)));
    }

    @Test
    public void testOperatorGreaterThanOrEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("age");
        condition.setOperator(Operator.GREATER_THAN_OR_EQUALS);
        condition.setValue(18);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 18)));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 19)));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 17)));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 16)));
    }

    @Test
    public void testOperatorLessThan() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("age");
        condition.setOperator(Operator.LESS_THAN);
        condition.setValue(18);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 17)));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 19)));
    }

    @Test
    public void testOperatorLessThanOrEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("age");
        condition.setOperator(Operator.LESS_THAN_OR_EQUALS);
        condition.setValue(18);
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 17)));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 18)));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 19)));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("age", 20)));
    }

    @Test
    public void testOperatorSemverEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_EQUALS);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.0.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "2.0.0")));
    }

    @Test
    public void testOperatorSemverNotEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_NOT_EQUALS);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "2.0.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.0.0")));
    }

    @Test
    public void testOperatorSemverGreaterThan() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_GREATER_THAN);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "2.0.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "0.9.0")));
    }

    @Test
    public void testOperatorSemverGreaterThanOrEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_GREATER_THAN_OR_EQUALS);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.0.0")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "2.0.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "0.9.0")));
    }

    @Test
    public void testOperatorSemverLessThan() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_LESS_THAN);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "0.9.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.1.0")));
    }

    @Test
    public void testOperatorSemverLessThanOrEquals() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("version");
        condition.setOperator(Operator.SEMVER_LESS_THAN_OR_EQUALS);
        condition.setValue("1.0.0");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.0.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("version", "1.1.0")));
    }

    @Test
    public void testSemverPrereleaseAndBuildMetadata() {
        Condition prerelease = new Condition();
        prerelease.setAttribute("version");
        prerelease.setOperator(Operator.SEMVER_LESS_THAN);
        prerelease.setValue("1.2.3");
        assertTrue(evaluationData.allConditionsAreMatched(prerelease, Map.of("version", "1.2.3-beta.1")));

        Condition build = new Condition();
        build.setAttribute("version");
        build.setOperator(Operator.SEMVER_EQUALS);
        build.setValue("1.2.3+build.9");
        assertTrue(evaluationData.allConditionsAreMatched(build, Map.of("version", "1.2.3+build.5")));
    }

    @Test
    public void testOperatorBefore() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("date");
        condition.setOperator(Operator.BEFORE);
        condition.setValue("2023-05-13T16:23:59Z");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-12T00:00:00Z")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-14T00:00:00Z")));

        // equivalent offset instant is not before
        condition.setValue("2023-05-13T17:23:59+01:00");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-13T16:23:59Z")));

        // strings without an explicit timezone are not portable
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-12T00:00:00")));
    }

    @Test
    public void testOperatorAfter() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("date");
        condition.setOperator(Operator.AFTER);
        condition.setValue("2023-05-13T16:23:59Z");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-14T00:00:00Z")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("date", "2023-05-12T00:00:00Z")));
    }

    @Test
    public void testSimpleConditionExactSingleCondition() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions.get(0), Map.of("browser_type", "chrome")));
    }

    @Test
    public void testSimpleConditionExactCondition() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
    }

    @Test
    public void testSimpleConditionEmptyConditions() {
        List<Condition> conditions = new ArrayList<>();

        // Empty conditions should match everything
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));
    }

    @Test
    public void testSimpleConditionExtraConditions() {
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setAttribute("browser_type");
        condition.setOperator(Operator.EQUALS);
        condition.setValue("chrome");
        conditions.add(condition);

        // match with extra context
        Map<String, Object> context = new HashMap<>();
        context.put("browser_type", "chrome");
        context.put("browser_version", "1.0");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testSimpleConditionMultipleConditions() {
        List<Condition> conditions = new ArrayList<>();

        Condition condition1 = new Condition();
        condition1.setAttribute("browser_type");
        condition1.setOperator(Operator.EQUALS);
        condition1.setValue("chrome");
        conditions.add(condition1);

        Condition condition2 = new Condition();
        condition2.setAttribute("browser_version");
        condition2.setOperator(Operator.EQUALS);
        condition2.setValue("1.0");
        conditions.add(condition2);

        // match
        Map<String, Object> context = new HashMap<>();
        context.put("browser_type", "chrome");
        context.put("browser_version", "1.0");
        context.put("foo", "bar");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testAndConditionOneCondition() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> innerCondition = new HashMap<>();
        innerCondition.put("attribute", "browser_type");
        innerCondition.put("operator", "equals");
        innerCondition.put("value", "chrome");
        andConditions.add(innerCondition);

        andCondition.put("and", andConditions);
        conditions.add(andCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));
    }

    @Test
    public void testAndConditionMultipleConditions() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        andConditions.add(condition1);

        Map<String, Object> condition2 = new HashMap<>();
        condition2.put("attribute", "browser_version");
        condition2.put("operator", "equals");
        condition2.put("value", "1.0");
        andConditions.add(condition2);

        andCondition.put("and", andConditions);
        conditions.add(andCondition);

        // match
        Map<String, Object> context = new HashMap<>();
        context.put("browser_type", "chrome");
        context.put("browser_version", "1.0");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        // not match
        context.clear();
        context.put("browser_type", "chrome");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testOrConditionOneCondition() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> innerCondition = new HashMap<>();
        innerCondition.put("attribute", "browser_type");
        innerCondition.put("operator", "equals");
        innerCondition.put("value", "chrome");
        orConditions.add(innerCondition);

        orCondition.put("or", orConditions);
        conditions.add(orCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
    }

    @Test
    public void testOrConditionMultipleConditions() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        orConditions.add(condition1);

        Map<String, Object> condition2 = new HashMap<>();
        condition2.put("attribute", "browser_version");
        condition2.put("operator", "equals");
        condition2.put("value", "1.0");
        orConditions.add(condition2);

        orCondition.put("or", orConditions);
        conditions.add(orCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_version", "1.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));
    }

    @Test
    public void testNotConditionOneCondition() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> notCondition = new HashMap<>();
        List<Map<String, Object>> notConditions = new ArrayList<>();

        Map<String, Object> innerCondition = new HashMap<>();
        innerCondition.put("attribute", "browser_type");
        innerCondition.put("operator", "equals");
        innerCondition.put("value", "chrome");
        notConditions.add(innerCondition);

        notCondition.put("not", notConditions);
        conditions.add(notCondition);

        // match (not chrome)
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox")));

        // not match (is chrome)
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
    }

    @Test
    public void testNotConditionMultipleConditions() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> notCondition = new HashMap<>();
        List<Map<String, Object>> notConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        notConditions.add(condition1);

        Map<String, Object> condition2 = new HashMap<>();
        condition2.put("attribute", "browser_version");
        condition2.put("operator", "equals");
        condition2.put("value", "1.0");
        notConditions.add(condition2);

        notCondition.put("not", notConditions);
        conditions.add(notCondition);

        // match (neither chrome nor version 1.0)
        Map<String, Object> context = new HashMap<>();
        context.put("browser_type", "firefox");
        context.put("browser_version", "2.0");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));

        // not match (both conditions match)
        context.clear();
        context.put("browser_type", "chrome");
        context.put("browser_version", "1.0");
        assertFalse(evaluationData.allConditionsAreMatched(conditions, context));

        // match (only one condition matches)
        context.clear();
        context.put("browser_type", "chrome");
        context.put("browser_version", "2.0");
        assertTrue(evaluationData.allConditionsAreMatched(conditions, context));
    }

    @Test
    public void testNotConditionNestedOrMeansNoneMatchAndEmptyNotIsFalse() {
        Map<String, Object> chromeCondition = new HashMap<>();
        chromeCondition.put("attribute", "browser_type");
        chromeCondition.put("operator", "equals");
        chromeCondition.put("value", "chrome");

        Map<String, Object> firefoxCondition = new HashMap<>();
        firefoxCondition.put("attribute", "browser_type");
        firefoxCondition.put("operator", "equals");
        firefoxCondition.put("value", "firefox");

        Map<String, Object> orCondition = new HashMap<>();
        orCondition.put("or", Arrays.asList(chromeCondition, firefoxCondition));

        Map<String, Object> notCondition = new HashMap<>();
        notCondition.put("not", List.of(orCondition));

        assertFalse(evaluationData.allConditionsAreMatched(List.of(notCondition), Map.of("browser_type", "chrome")));
        assertTrue(evaluationData.allConditionsAreMatched(List.of(notCondition), Map.of("browser_type", "edge")));

        Map<String, Object> emptyNotCondition = new HashMap<>();
        emptyNotCondition.put("not", List.of());
        assertFalse(evaluationData.allConditionsAreMatched(List.of(emptyNotCondition), Map.of()));
    }

    @Test
    public void testNestedConditionsOrInsideAnd() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        andConditions.add(condition1);

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> orCondition1 = new HashMap<>();
        orCondition1.put("attribute", "browser_version");
        orCondition1.put("operator", "equals");
        orCondition1.put("value", "1.0");
        orConditions.add(orCondition1);

        Map<String, Object> orCondition2 = new HashMap<>();
        orCondition2.put("attribute", "browser_version");
        orCondition2.put("operator", "equals");
        orCondition2.put("value", "2.0");
        orConditions.add(orCondition2);

        orCondition.put("or", orConditions);
        andConditions.add(orCondition);
        andCondition.put("and", andConditions);
        conditions.add(andCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome", "browser_version", "1.0")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome", "browser_version", "2.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome", "browser_version", "3.0")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_version", "2.0")));
    }

    @Test
    public void testNestedConditionsPlainConditionsFollowedByOrInsideAnd() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> plainCondition = new HashMap<>();
        plainCondition.put("attribute", "country");
        plainCondition.put("operator", "equals");
        plainCondition.put("value", "nl");
        conditions.add(plainCondition);

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        andConditions.add(condition1);

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> orCondition1 = new HashMap<>();
        orCondition1.put("attribute", "browser_version");
        orCondition1.put("operator", "equals");
        orCondition1.put("value", "1.0");
        orConditions.add(orCondition1);

        Map<String, Object> orCondition2 = new HashMap<>();
        orCondition2.put("attribute", "browser_version");
        orCondition2.put("operator", "equals");
        orCondition2.put("value", "2.0");
        orConditions.add(orCondition2);

        orCondition.put("or", orConditions);
        andConditions.add(orCondition);
        andCondition.put("and", andConditions);
        conditions.add(andCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "nl", "browser_type", "chrome", "browser_version", "1.0")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "nl", "browser_type", "chrome", "browser_version", "2.0")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome", "browser_version", "3.0")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "us", "browser_version", "2.0")));
    }

    @Test
    public void testNestedConditionsAndInsideOr() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        orConditions.add(condition1);

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> andCondition1 = new HashMap<>();
        andCondition1.put("attribute", "browser_version");
        andCondition1.put("operator", "equals");
        andCondition1.put("value", "1.0");
        andConditions.add(andCondition1);

        Map<String, Object> andCondition2 = new HashMap<>();
        andCondition2.put("attribute", "country");
        andCondition2.put("operator", "equals");
        andCondition2.put("value", "nl");
        andConditions.add(andCondition2);

        andCondition.put("and", andConditions);
        orConditions.add(andCondition);
        orCondition.put("or", orConditions);
        conditions.add(orCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_version", "1.0", "country", "nl")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "firefox", "browser_version", "1.0")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_version", "1.0", "country", "us")));
    }

    @Test
    public void testNestedConditionsPlainConditionsFollowedByAndInsideOr() {
        List<Map<String, Object>> conditions = new ArrayList<>();

        Map<String, Object> plainCondition = new HashMap<>();
        plainCondition.put("attribute", "country");
        plainCondition.put("operator", "equals");
        plainCondition.put("value", "nl");
        conditions.add(plainCondition);

        Map<String, Object> orCondition = new HashMap<>();
        List<Map<String, Object>> orConditions = new ArrayList<>();

        Map<String, Object> condition1 = new HashMap<>();
        condition1.put("attribute", "browser_type");
        condition1.put("operator", "equals");
        condition1.put("value", "chrome");
        orConditions.add(condition1);

        Map<String, Object> andCondition = new HashMap<>();
        List<Map<String, Object>> andConditions = new ArrayList<>();

        Map<String, Object> andCondition1 = new HashMap<>();
        andCondition1.put("attribute", "browser_version");
        andCondition1.put("operator", "equals");
        andCondition1.put("value", "1.0");
        andConditions.add(andCondition1);

        Map<String, Object> andCondition2 = new HashMap<>();
        andCondition2.put("attribute", "device_type");
        andCondition2.put("operator", "equals");
        andCondition2.put("value", "mobile");
        andConditions.add(andCondition2);

        andCondition.put("and", andConditions);
        orConditions.add(andCondition);
        orCondition.put("or", orConditions);
        conditions.add(orCondition);

        // match
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "nl", "browser_type", "chrome")));
        assertTrue(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "nl", "browser_version", "1.0", "device_type", "mobile")));

        // not match
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("browser_type", "chrome")));
        assertFalse(evaluationData.allConditionsAreMatched(conditions, Map.of("country", "nl", "browser_version", "1.0")));
    }
}
