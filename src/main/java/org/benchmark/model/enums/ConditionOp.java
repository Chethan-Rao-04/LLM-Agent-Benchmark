package org.benchmark.model.enums;

public enum ConditionOp {
    EQ("=="),
    NE("!="),
    GT(">"),
    LT("<"),
    GTE(">="),
    LTE("<=");

    private final String symbol;

    ConditionOp(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
