package org.benchmark.mcp.runtime;

/**
 * Captures one benchmark-tool execution attempt for later scoring.
 *
 * @param toolName resolved tool name
 * @param commandName resolved command name
 * @param option option submitted by the model
 * @param success whether the simulator accepted and executed the command
 * @param message execution output or error text
 */
public record BenchmarkExecutionRecord(String toolName,
                                       String commandName,
                                       String option,
                                       boolean success,
                                       String message) {
}
