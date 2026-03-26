package org.benchmark.mcp.runtime;

/**
 * Input schema for one generated benchmark tool callback.
 *
 * @param command command name to execute within the selected tool
 * @param option single option flag, or empty string when no option is required
 */
public record BenchmarkToolExecutionRequest(String command, String option) {
}
