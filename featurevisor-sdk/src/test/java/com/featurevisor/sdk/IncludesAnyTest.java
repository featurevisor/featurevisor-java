package com.featurevisor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncludesAnyTest {
    @Test
    void primitiveArraysAndNestedConditions() {
        Object[][] cases = {
            {"mixed", Arrays.asList("red", 1, true, null), Arrays.asList("absent", 1.0), true, true},
            {"null", Arrays.asList((Object) null), Arrays.asList((Object) null), true, true},
            {"boolean", List.of(false), List.of(false), true, true},
            {"number not boolean", List.of(1), List.of(true), true, false},
            {"boolean not number", List.of(true), List.of(1), true, false},
            {"string not number", List.of("1"), List.of(1), true, false},
            {"string not boolean", List.of("true"), List.of(true), true, false},
            {"duplicates", List.of("red", "red"), List.of("blue", "red", "red"), true, true},
            {"empty actual", List.of(), Arrays.asList((Object) null), true, false},
            {"empty candidates", List.of(1), List.of(), true, false},
            {"both empty", List.of(), List.of(), true, false},
            {"structured actual", List.of(Map.of(), List.of(1), "red"), List.of("red"), true, true},
            {"structured ignored", List.of(Map.of(), List.of(1)), Arrays.asList(null, 1), true, false},
            {"object after match", List.of(1), List.of(1, Map.of()), false, false},
            {"array after match", List.of(1), List.of(1, List.of()), false, false},
            {"object first", List.of(1), List.of(Map.of(), 1), false, false},
            {"null actual", null, List.of(), false, false},
            {"object actual", Map.of(), List.of(), false, false},
            {"scalar actual", "red", List.of("red"), false, false},
            {"null candidates", List.of(), null, false, false},
            {"scalar candidates", List.of("red"), "red", false, false},
            {"object candidates", List.of(), Map.of(), false, false},
        };
        for (Object[] row : cases) {
            for (Operator op : new Operator[]{Operator.INCLUDES_ANY, Operator.NOT_INCLUDES_ANY}) {
                boolean want = (boolean) row[3] && ((boolean) row[4] == (op == Operator.INCLUDES_ANY));
                Map<String, Object> nested = new HashMap<>();
                nested.put("items", row[1]);
                Condition leaf = new Condition();
                leaf.setAttribute("nested.items");
                leaf.setOperator(op);
                leaf.setValue(row[2]);
                assertEquals(want, Conditions.conditionIsMatched(leaf, Map.of("nested", nested), (pattern, flags) -> null), row[0] + op.toString());
                assertFalse(Conditions.conditionIsMatched(leaf, Map.of(), (pattern, flags) -> null));
                if (!(boolean) row[3]) {
                    Condition not = new Condition();
                    not.setNot(List.of(leaf));
                    assertTrue(Conditions.conditionIsMatched(not, Map.of("nested", nested), (pattern, flags) -> null));
                    Condition or = new Condition();
                    or.setOr(List.of(leaf));
                    assertFalse(Conditions.conditionIsMatched(or, Map.of("nested", nested), (pattern, flags) -> null));
                }
            }
        }
    }

    @Test
    void featureAndGlobalRequirementsShareComparison() throws Exception {
        Object[][] cases = {
            {"includesAny", "[1.0]", true}, {"notIncludesAny", "[false]", true},
            {"includesAny", "[true]", false}, {"notIncludesAny", "[1]", false},
            {"includesAny", "[]", false}, {"notIncludesAny", "[]", true},
            {"includesAny", "[1,{}]", false}, {"notIncludesAny", "[1,[]]", false},
        };
        for (Object[] row : cases) {
            String raw = "{\"schemaVersion\":\"2\",\"revision\":\"any\",\"segments\":{},"
                + "\"features\":{\"gate\":{\"bucketBy\":\"userId\",\"conditions\":[{\"variable\":\"source\",\"path\":[\"items\"],\"operator\":\"OP\",\"value\":CANDIDATES}],\"traffic\":[{\"key\":\"all\",\"segments\":\"*\",\"percentage\":100000,\"enabled\":true}]}},"
                + "\"variables\":{\"source\":{\"type\":\"object\",\"defaultValue\":{\"items\":[1,null,{\"x\":1}]}},"
                + "\"gate\":{\"type\":\"boolean\",\"defaultValue\":true,\"disabledValue\":false,\"conditions\":[{\"variable\":\"source\",\"path\":[\"items\"],\"operator\":\"OP\",\"value\":CANDIDATES}]}}}";
            raw = raw.replace("OP", (String) row[0]).replace("CANDIDATES", (String) row[1]);
            Featurevisor sdk = Featurevisor.createFeaturevisor(new Featurevisor.FeaturevisorOptions()
                .datafile(new ObjectMapper().readValue(raw, DatafileContent.class)).logLevel(FeaturevisorLogLevel.FATAL));
            try {
                assertEquals(row[2], sdk.isEnabled("gate", Map.of("userId", "user")), Arrays.toString(row));
                assertEquals(row[2], sdk.getVariableBoolean("gate"), Arrays.toString(row));
            } finally { sdk.close(); }
        }
    }
}
