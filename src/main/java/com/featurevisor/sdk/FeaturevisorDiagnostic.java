package com.featurevisor.sdk;

import java.util.HashMap;
import java.util.Map;

public class FeaturevisorDiagnostic {
    private FeaturevisorLogLevel level = FeaturevisorLogLevel.INFO;
    private String code;
    private String message;
    private String module;
    private String moduleName;
    private Object originalError;
    private Map<String, Object> details = new HashMap<>();

    public FeaturevisorDiagnostic() {}

    public FeaturevisorDiagnostic(FeaturevisorLogLevel level, String code, String message) {
        this.level = level;
        this.code = code;
        this.message = message;
    }

    public FeaturevisorLogLevel getLevel() { return level; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getModule() { return module; }
    public String getModuleName() { return moduleName; }
    public Object getOriginalError() { return originalError; }
    public Map<String, Object> getDetails() { return details; }

    public void setLevel(FeaturevisorLogLevel level) { this.level = level; }
    public void setCode(String code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setModule(String module) { this.module = module; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public void setOriginalError(Object originalError) { this.originalError = originalError; }
    public void setDetails(Map<String, Object> details) {
        this.details = details != null ? details : new HashMap<>();
    }

    public FeaturevisorDiagnostic level(FeaturevisorLogLevel level) {
        this.level = level;
        return this;
    }

    public FeaturevisorDiagnostic code(String code) {
        this.code = code;
        return this;
    }

    public FeaturevisorDiagnostic message(String message) {
        this.message = message;
        return this;
    }

    public FeaturevisorDiagnostic module(String module) {
        this.module = module;
        return this;
    }

    public FeaturevisorDiagnostic moduleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    public FeaturevisorDiagnostic originalError(Object originalError) {
        this.originalError = originalError;
        return this;
    }

    public FeaturevisorDiagnostic details(Map<String, Object> details) {
        setDetails(details);
        return this;
    }
}
