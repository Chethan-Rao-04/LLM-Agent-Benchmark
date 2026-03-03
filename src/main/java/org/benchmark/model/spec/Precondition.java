package org.benchmark.model.spec;

import org.benchmark.model.enums.ConditionOp;

// Defines checks before executing a command
public record Precondition(String variable, ConditionOp operator, String value) {
}
