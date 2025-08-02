package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import java.util.List;

public class Condition {
    // This class represents the Condition type which can be:
    // - PlainCondition: Object with attribute, operator, value
    // - AndCondition: Object with "and" property
    // - OrCondition: Object with "or" property
    // - NotCondition: Object with "not" property
    // - String: Direct string value

    @JsonProperty("attribute")
    private String attribute;

    @JsonProperty("operator")
    private Operator operator;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("regexFlags")
    private String regexFlags;

    @JsonProperty("and")
    private List<Condition> and;

    @JsonProperty("or")
    private List<Condition> or;

    @JsonProperty("not")
    private List<Condition> not;

    // For string condition
    private String stringCondition;

    // Constructors
    public Condition() {}

    public Condition(String stringCondition) {
        this.stringCondition = stringCondition;
    }

    public Condition(String attribute, Operator operator) {
        this.attribute = attribute;
        this.operator = operator;
    }

    public Condition(String attribute, Operator operator, Object value) {
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
    }

    // Getters and Setters
    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getRegexFlags() {
        return regexFlags;
    }

    public void setRegexFlags(String regexFlags) {
        this.regexFlags = regexFlags;
    }

    public List<Condition> getAnd() {
        return and;
    }

    public void setAnd(List<Condition> and) {
        this.and = and;
    }

    public List<Condition> getOr() {
        return or;
    }

    public void setOr(List<Condition> or) {
        this.or = or;
    }

    public List<Condition> getNot() {
        return not;
    }

    public void setNot(List<Condition> not) {
        this.not = not;
    }

    public String getStringCondition() {
        return stringCondition;
    }

    public void setStringCondition(String stringCondition) {
        this.stringCondition = stringCondition;
    }

    // Helper methods to determine type
    public boolean isPlainCondition() {
        return attribute != null && operator != null;
    }

    public boolean isAndCondition() {
        return and != null;
    }

    public boolean isOrCondition() {
        return or != null;
    }

    public boolean isNotCondition() {
        return not != null;
    }

    public boolean isStringCondition() {
        return stringCondition != null;
    }


}
