package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ProjectConfig {
    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("environments")
    private List<String> environments;

    public ProjectConfig() {}

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments;
    }
}
