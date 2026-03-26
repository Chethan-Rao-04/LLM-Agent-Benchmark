package org.benchmark.model.enums;

/**
 * Supported comparison operators for command preconditions.
 */
public enum ConditionOp {
    /** Equal to. */
    EQ("=="),
    /** Not equal to. */
    NE("!="),
    /** Greater than. */
    GT(">"),
    /** Less than. */
    LT("<"),
    /** Greater than or equal to. */
    GTE(">="),
    /** Less than or equal to. */
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
