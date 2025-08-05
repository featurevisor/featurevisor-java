package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;

/**
 * Main entry point for Featurevisor SDK
 * Provides factory methods for creating SDK instances
 */
public class Featurevisor {

    /**
     * Create a new Featurevisor instance
     * @param options The instance options
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(FeaturevisorInstance.InstanceOptions options) {
        if (options == null) {
            options = new FeaturevisorInstance.InstanceOptions();
        }
        return new FeaturevisorInstance(options);
    }

    /**
     * Create a new Featurevisor instance with default options
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance() {
        return createInstance(new FeaturevisorInstance.InstanceOptions());
    }

    /**
     * Create a new Featurevisor instance with datafile
     * @param datafile The datafile content
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(com.featurevisor.types.DatafileContent datafile) {
        return createInstance(new FeaturevisorInstance.InstanceOptions().datafile(datafile));
    }

    /**
     * Create a new Featurevisor instance with datafile string
     * @param datafileString The datafile as JSON string
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(String datafileString) {
        return createInstance(new FeaturevisorInstance.InstanceOptions().datafileString(datafileString));
    }

    /**
     * Create a new Featurevisor instance with context
     * @param context The context map
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(Map<String, Object> context) {
        return createInstance(new FeaturevisorInstance.InstanceOptions().context(context));
    }

    /**
     * Create a new Featurevisor instance with log level
     * @param logLevel The log level
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(Logger.LogLevel logLevel) {
        return createInstance(new FeaturevisorInstance.InstanceOptions().logLevel(logLevel));
    }

    /**
     * Create a new Featurevisor instance with logger
     * @param logger The logger instance
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(Logger logger) {
        return createInstance(new FeaturevisorInstance.InstanceOptions().logger(logger));
    }

    /**
     * Create a new Featurevisor instance with sticky features
     * @param sticky The sticky features map
     * @return A new Featurevisor instance
     */
    public static FeaturevisorInstance createInstance(Map<String, Object> sticky, boolean isSticky) {
        if (isSticky) {
            return createInstance(new FeaturevisorInstance.InstanceOptions().sticky(sticky));
        } else {
            return createInstance(new FeaturevisorInstance.InstanceOptions().context(sticky));
        }
    }
}
