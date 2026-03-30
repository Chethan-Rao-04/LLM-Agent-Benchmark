package org.benchmark.model.objects;

/**
 * One step in a multi-command workflow within a single tool.
 *
 * @param commandName the command to execute
 * @param optionName  the option to pass (empty string if none)
 * @param description human-readable description of why this step is needed
 */
public record WorkflowStep(String commandName, String optionName, String description) {
}
