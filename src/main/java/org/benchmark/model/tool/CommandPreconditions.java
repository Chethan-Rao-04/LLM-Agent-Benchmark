package org.benchmark.model.documentation;

// This record defines checks before executing a commands (for complex tools only)
public record CommandPreconditions(String variable, String operator, String value) {
}

