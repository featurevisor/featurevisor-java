package com.featurevisor.sdk;

import java.util.HashMap;
import java.util.Map;

public final class FeaturevisorEventDetails extends HashMap<String, Object> {
    public FeaturevisorEventDetails() {
        super();
    }

    public FeaturevisorEventDetails(Map<String, Object> map) {
        super(map);
    }
}
