package org.benchmark.model.enums;

/**
 * Supported state mutation operations for command effects.
 */
public enum EffectOp {
    /** Assigns a value to the target variable. */
    ASSIGN,
    /** Increments numeric value by one. */
    INCREMENT,
    /** Decrements numeric value by one. */
    DECREMENT,
    /** Deletes/removes the target variable. */
    DELETE
}
