package org.benchmark.model.documentation;

// This record defines checks before executing a commands (for complex tools only)
public record CommandPreCheck(   String variable, String operator, String value) {
}

