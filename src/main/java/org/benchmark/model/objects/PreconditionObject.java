package org.benchmark.model.objects;

import org.benchmark.model.enums.ConditionOp;

/**
 * Immutable precondition check for a command.
 *
 * @param variable state variable to evaluate
 * @param operator comparison operator
 * @param value expected value
 */
public record PreconditionObject(String variable, ConditionOp operator, String value) {
}
