package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HelpersTest {

    @Test
    public void testStringMismatchReturnsNull() {
        assertNull(Helpers.getValueByType(1, "string"));
    }

    @Test
    public void testStringValueReturnedAsIs() {
        assertEquals("1", Helpers.getValueByType("1", "string"));
    }

    @Test
    public void testBooleanValueReturnedAsIs() {
        assertEquals(Boolean.TRUE, Helpers.getValueByType(true, "boolean"));
    }

    @Test
    public void testBooleanStrictness() {
        assertEquals(Boolean.FALSE, Helpers.getValueByType("true", "boolean"));
        assertEquals(Boolean.FALSE, Helpers.getValueByType(1, "boolean"));
    }

    @Test
    public void testObjectValueReturnedAsIs() {
        Map<String, Integer> value = Map.of("a", 1, "b", 2);
        assertEquals(value, Helpers.getValueByType(value, "object"));
    }

    @Test
    public void testJsonValueReturnedAsIs() {
        String json = "{\"a\":1,\"b\":2}";
        assertEquals(json, Helpers.getValueByType(json, "json"));
    }

    @Test
    public void testArrayValueReturnedAsIs() {
        List<String> arr = List.of("1", "2", "3");
        assertEquals(arr, Helpers.getValueByType(arr, "array"));
    }

    @Test
    public void testIntegerParsing() {
        assertEquals(Integer.valueOf(1), Helpers.getValueByType("1", "integer"));
    }

    @Test
    public void testDoubleParsing() {
        assertEquals(1.1d, Helpers.getValueByType("1.1", "double"));
    }

    @Test
    public void testNullValueReturnsNull() {
        assertNull(Helpers.getValueByType(null, "string"));
    }
}
