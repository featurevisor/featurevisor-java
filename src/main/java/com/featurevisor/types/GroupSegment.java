package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * This class represents the GroupSegment type which can be:
 * - PlainGroupSegment: String (SegmentKey)
 * - AndGroupSegment: Object with "and" property
 * - OrGroupSegment: Object with "or" property
 * - NotGroupSegment: Object with "not" property
 */
public class GroupSegment {
    @JsonProperty("and")
    private List<GroupSegment> and;

    @JsonProperty("or")
    private List<GroupSegment> or;

    @JsonProperty("not")
    private List<GroupSegment> not;

    // For plain group segment (single string)
    private String plainGroupSegment;

    // Constructors
    public GroupSegment() {}

    public GroupSegment(String plainGroupSegment) {
        this.plainGroupSegment = plainGroupSegment;
    }

    public GroupSegment(List<GroupSegment> segments, GroupSegmentType type) {
        switch (type) {
            case AND:
                this.and = segments;
                break;
            case OR:
                this.or = segments;
                break;
            case NOT:
                this.not = segments;
                break;
        }
    }

    // Getters and Setters
    public List<GroupSegment> getAnd() {
        return and;
    }

    public void setAnd(List<GroupSegment> and) {
        this.and = and;
    }

    public List<GroupSegment> getOr() {
        return or;
    }

    public void setOr(List<GroupSegment> or) {
        this.or = or;
    }

    public List<GroupSegment> getNot() {
        return not;
    }

    public void setNot(List<GroupSegment> not) {
        this.not = not;
    }

    public String getPlainGroupSegment() {
        return plainGroupSegment;
    }

    public void setPlainGroupSegment(String plainGroupSegment) {
        this.plainGroupSegment = plainGroupSegment;
    }

    // Helper methods to determine type
    public boolean isPlainGroupSegment() {
        return plainGroupSegment != null;
    }

    public boolean isAndGroupSegment() {
        return and != null;
    }

    public boolean isOrGroupSegment() {
        return or != null;
    }

    public boolean isNotGroupSegment() {
        return not != null;
    }

    // Get the actual value regardless of type
    public Object getValue() {
        if (isPlainGroupSegment()) {
            return plainGroupSegment;
        } else if (isAndGroupSegment()) {
            return and;
        } else if (isOrGroupSegment()) {
            return or;
        } else if (isNotGroupSegment()) {
            return not;
        }
        return null;
    }


}
