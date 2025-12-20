package org.benchmark.model;

//TODO - check if needed

import java.util.Map;

// This acts as the tool's memory, keeps track of the tool states ( experimental)
public record ToolStateMemory( Map<String, String> toolStateVariables) {

}
