package com.featurevisor.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncludesAnyCheckTest {
    @Test
    void literalCompareToAndStructuredErrors() throws Exception {
        List<Map<String, Object>> rows = new ObjectMapper().readValue(
            "[{\"name\":\"mixed\",\"actual\":[\"red\",1,true,null],\"candidates\":[\"absent\",1],\"valid\":true,\"found\":true},{\"name\":\"null\",\"actual\":[null],\"candidates\":[null],\"valid\":true,\"found\":true},{\"name\":\"boolean\",\"actual\":[false],\"candidates\":[false],\"valid\":true,\"found\":true},{\"name\":\"number not bool\",\"actual\":[1],\"candidates\":[true],\"valid\":true,\"found\":false},{\"name\":\"bool not number\",\"actual\":[true],\"candidates\":[1],\"valid\":true,\"found\":false},{\"name\":\"string not number\",\"actual\":[\"1\"],\"candidates\":[1],\"valid\":true,\"found\":false},{\"name\":\"string not bool\",\"actual\":[\"true\"],\"candidates\":[true],\"valid\":true,\"found\":false},{\"name\":\"duplicates\",\"actual\":[\"red\",\"red\"],\"candidates\":[\"blue\",\"red\",\"red\"],\"valid\":true,\"found\":true},{\"name\":\"empty actual\",\"actual\":[],\"candidates\":[null],\"valid\":true,\"found\":false},{\"name\":\"empty candidate\",\"actual\":[1],\"candidates\":[],\"valid\":true,\"found\":false},{\"name\":\"both empty\",\"actual\":[],\"candidates\":[],\"valid\":true,\"found\":false},{\"name\":\"structured actual\",\"actual\":[{},[1],\"red\"],\"candidates\":[\"red\"],\"valid\":true,\"found\":true},{\"name\":\"structured ignored\",\"actual\":[{},[1]],\"candidates\":[null,1],\"valid\":true,\"found\":false},{\"name\":\"object after match\",\"actual\":[1],\"candidates\":[1,{}],\"valid\":false,\"found\":false},{\"name\":\"array after match\",\"actual\":[1],\"candidates\":[1,[]],\"valid\":false,\"found\":false},{\"name\":\"object first\",\"actual\":[1],\"candidates\":[{},1],\"valid\":false,\"found\":false},{\"name\":\"null actual\",\"actual\":null,\"candidates\":[],\"valid\":false,\"found\":false},{\"name\":\"object actual\",\"actual\":{},\"candidates\":[],\"valid\":false,\"found\":false},{\"name\":\"scalar actual\",\"actual\":\"red\",\"candidates\":[\"red\"],\"valid\":false,\"found\":false},{\"name\":\"null candidates\",\"actual\":[],\"candidates\":null,\"valid\":false,\"found\":false},{\"name\":\"scalar candidates\",\"actual\":[\"red\"],\"candidates\":\"red\",\"valid\":false,\"found\":false},{\"name\":\"object candidates\",\"actual\":[],\"candidates\":{},\"valid\":false,\"found\":false}]", new TypeReference<List<Map<String, Object>>>() {});
        for (Map<String, Object> row : rows) {
            for (String op : List.of("includesAny", "notIncludesAny")) {
                for (boolean compareTo : new boolean[]{false, true}) {
                    Map<String, Object> actual = new HashMap<>();
                    actual.put("items", row.get("actual"));
                    VariableChecks checker = new VariableChecks(reference -> VariableChecks.Resolution.value(
                        "subject".equals(reference.get("variable")) ? actual : row.get("candidates")));
                    Map<String, Object> leaf = new HashMap<>(Map.of("variable", "subject", "path", "items", "operator", op));
                    if (compareTo) leaf.put("compareTo", Map.of("variable", "other"));
                    else leaf.put("value", row.get("candidates"));
                    boolean valid = (boolean) row.get("valid");
                    boolean want = valid && ((boolean) row.get("found") == op.equals("includesAny"));
                    String label = row.get("name") + " " + op + " compareTo=" + compareTo;
                    List<VariableChecks.CheckFailure> result = checker.evaluateStructured(List.of(leaf));
                    assertEquals(want, result.isEmpty(), label);
                    assertEquals(!valid, result.stream().anyMatch(error -> error.message.contains(" error:")), label);
                    if (!valid) {
                        assertFalse(result.get(0).path.isEmpty(), label);
                        for (String group : List.of("not", "or")) {
                            List<VariableChecks.CheckFailure> grouped = checker.evaluateStructured(List.of(Map.of(group, List.of(leaf))));
                            assertFalse(grouped.isEmpty(), label + group);
                            assertTrue(grouped.get(0).message.contains(" error:"), label + group);
                        }
                    }
                }
            }
        }
    }

    @Test
    void missingPathsAndSkippedOperands() {
        VariableChecks checker = new VariableChecks(reference -> VariableChecks.Resolution.value(Map.of()));
        for (String op : List.of("includesAny", "notIncludesAny")) {
            List<VariableChecks.CheckFailure> missing = checker.evaluateStructured(List.of(
                Map.of("variable", "subject", "path", "missing", "operator", op, "value", List.of())));
            assertEquals(1, missing.size());
            assertFalse(missing.get(0).message.contains(" error:"));
            List<VariableChecks.CheckFailure> skipped = checker.evaluateStructured(List.of(Map.of("or", List.of(
                Map.of("variable", "subject", "operator", "exists"),
                Map.of("variable", "subject", "operator", op, "compareTo", Map.of("variable", "other"))))));
            assertTrue(skipped.isEmpty(), skipped.toString());
        }
    }
}
