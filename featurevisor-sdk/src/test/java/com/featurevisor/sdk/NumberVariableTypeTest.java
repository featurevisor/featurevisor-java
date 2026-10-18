package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class NumberVariableTypeTest {

    @Test
    public void readsNumberAndEarlierDoubleVariablesTheSameWay() throws Exception {
        String datafileJson = """
            {
              "schemaVersion": "4",
              "revision": "1",
              "segments": {},
              "features": {},
              "variables": {
                "rate": { "type": "number", "defaultValue": 2.5 },
                "ratio": { "type": "double", "defaultValue": 1.5 }
              }
            }
            """;

        DatafileContent datafile = DatafileContent.fromJson(datafileJson);
        assertEquals(VariableType.NUMBER, datafile.getVariables().get("rate").getType());
        assertEquals(VariableType.DOUBLE, datafile.getVariables().get("ratio").getType());

        Featurevisor sdk = Featurevisor.createFeaturevisor(
            new Featurevisor.FeaturevisorOptions().datafile(datafile)
        );
        assertEquals(2.5, sdk.getVariableDouble("rate"));
        assertEquals(1.5, sdk.getVariableDouble("ratio"));
        sdk.close();
    }
}
