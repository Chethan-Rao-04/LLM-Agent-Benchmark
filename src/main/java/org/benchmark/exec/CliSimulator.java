package org.benchmark.exec;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.model.enums.StateScope;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.StateRequirement;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Minimal in-memory CLI simulator used by benchmark evaluation.
 *
 * <p>The simulator receives a tool plus a requested command name, resolves the
 * command inside the tool, validates the option, and then applies the command
 * effects to the runtime state.</p>
 */
@Slf4j
@Component
public class CliSimulator {

    /**
     * Resolves the requested command inside the tool, validates the option, and
     * applies the command effects when the request is valid.
     *
     * @param tool tool chosen by the agent
     * @param commandName command name requested by the agent
     * @param option chosen option, or {@code null}
     * @param stateManager session state storage
     * @param sessionId session identifier
     * @return execution status including resolved command name and output text
     */
    public ExecutionResult execute(ToolObject tool,
                                   String commandName,
                                   String option,
                                   SessionStateManager stateManager,
                                   String sessionId) {
        String trimmedOptionName = CommandOptionNormalizer.normalize(option);
        Map<String, String> beforeState = new java.util.HashMap<>(
                stateManager.getToolStateSnapshot(sessionId, tool.name()));
        log.debug("CLI execution tool={} command={} option={}", tool.name(), commandName, trimmedOptionName);

        CommandObject command = findCommand(tool, commandName);
        if (command == null) {
            String message = "Unknown command: " + commandName + " for tool " + tool.name();
            log.debug("CLI execution rejected: {}", message);
            return ExecutionResult.failure(commandName, message);
        }

        String preconditionFailure = checkPreconditions(command, stateManager, sessionId, tool.name());
        if (preconditionFailure != null) {
            log.debug("CLI execution rejected: {}", preconditionFailure);
            return ExecutionResult.failure(command.name(), preconditionFailure);
        }

        String optionFailure = validateOption(command, trimmedOptionName);
        if (optionFailure != null) {
            log.debug("CLI execution rejected: {}", optionFailure);
            return ExecutionResult.failure(command.name(), optionFailure);
        }

        CommandEffectApplier.applyEffects(
                command.commandEffectObjects(), trimmedOptionName, stateManager, sessionId, tool.name());
        Map<String, String> afterState = stateManager.getToolStateSnapshot(sessionId, tool.name());
        log.debug("CLI state transition tool={} before={} after={}",
                tool.name(), formatState(beforeState), formatState(afterState));
        return ExecutionResult.success(command.name(), "OK: " + command.name());
    }

    private CommandObject findCommand(ToolObject tool, String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return null;
        }
        return tool.commands().stream()
                .filter(command -> command.name().equalsIgnoreCase(commandName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks whether all preconditions for the command are satisfied in the current state.
     *
     * @return error message if any precondition is not met, or {@code null} if all pass
     */
    private String checkPreconditions(CommandObject command,
                                       SessionStateManager stateManager,
                                       String sessionId,
                                       String toolName) {
        if (command.preconditions() == null || command.preconditions().isEmpty()) {
            if (command.scopedPreconditions() == null || command.scopedPreconditions().isEmpty()) {
                return null;
            }
        }

        if (command.scopedPreconditions() == null || command.scopedPreconditions().isEmpty()) {
            return null;
        }

        for (StateRequirement required : command.scopedPreconditions()) {
            String actual = stateManager.getState(sessionId, toolName, required.scope(), required.variable());
            if (!required.value().equals(actual)) {
                return "Precondition not met for command: " + command.name()
                        + ". Required " + formatScope(required.scope()) + ": {"
                        + required.variable() + "=" + required.value()
                        + "}, actual: {" + required.variable() + "=" + actual + "}";
            }
        }
        return null;
    }

    private String formatScope(StateScope scope) {
        return scope == StateScope.SHARED ? "shared state" : "local state";
    }

    /**
     * Validates whether a provided option belongs to the command option set.
     */
    private String validateOption(CommandObject command, String option) {
        Set<String> validOptions = new HashSet<>();

        if (command.commandOptions() != null) {
            for (OptionEntity spec : command.commandOptions()) {
                validOptions.add(spec.optionName());
            }
        }

        if (option.isBlank()) {
            if (!validOptions.isEmpty()) {
                return "Missing required option for command " + command.name();
            }
            return null;
        }

        if (!validOptions.contains(option)) {
            return "Unknown option " + option + " for command " + command.name();
        }
        return null;
    }

    /**
     * Result of command execution in the CLI simulator.
     *
     * @param success whether execution succeeded
     * @param exitCode process-like exit code
     * @param stdout simulated standard output
     * @param stderr simulated standard error
     * @param resolvedCommandName canonical command name used for logging
     */
    public record ExecutionResult(boolean success,
                                  int exitCode,
                                  String stdout,
                                  String stderr,
                                  String resolvedCommandName) {

        private static ExecutionResult success(String resolvedCommandName, String stdout) {
            return new ExecutionResult(true, 0, stdout, "", resolvedCommandName);
        }

        private static ExecutionResult failure(String resolvedCommandName, String stderr) {
            return new ExecutionResult(false, 2, "", stderr, resolvedCommandName);
        }
    }

    private String formatState(Map<String, String> state) {
        if (state == null || state.isEmpty()) {
            return "{}";
        }
        return new TreeMap<>(state).toString();
    }
}
