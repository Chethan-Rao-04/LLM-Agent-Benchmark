package org.benchmark.exec;

import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Minimal in-memory CLI simulator used by benchmark evaluation.
 *
 * <p>
 * The simulator validates command preconditions and option values,
 * then applies command effects to session state.
 * </p>
 */
@Slf4j
@Component
public class CliSimulator {

    /**
     * Executes a command against session state.
     *
     * @param cmd          command specification to execute
     * @param option       chosen option (may be {@code null})
     * @param stateManager session state storage
     * @param sessionId    session identifier
     * @param toolName     tool whose environment is being executed against
     * @return execution status including exit code and output text
     */
    public ExecutionResult execute(CommandObject cmd,
            String option,
            SessionStateManager stateManager,
            String sessionId,
            String toolName) {
        Map<String, String> beforeState = new java.util.HashMap<>(
                stateManager.getToolStateSnapshot(sessionId, toolName));
        String normalizedOption = option == null || option.isBlank() ? "<none>" : option;
        log.debug("[CLI] tool={} command={} option={}", toolName, cmd.name(), normalizedOption);

        if (!validatePreconditions(cmd, stateManager, sessionId, toolName)) {
            log.debug("[CLI] blocked preconditions state={}", formatState(beforeState));
            return new ExecutionResult(false, 2, "", "Preconditions not met for command: " + cmd.name());
        }

        if (!validateOption(cmd, option)) {
            log.debug("[CLI] invalid option={} command={}", normalizedOption, cmd.name());
            return new ExecutionResult(false, 2, "", "Unknown option " + option + " for command " + cmd.name());
        }

        CommandEffectApplier.applyEffects(cmd.commandEffectObjects(), option, stateManager, sessionId, toolName);
        Map<String, String> afterState = stateManager.getToolStateSnapshot(sessionId, toolName);
        log.debug("[CLI] state {} -> {}", formatState(beforeState), formatState(afterState));
        return new ExecutionResult(true, 0, "OK: " + cmd.name(), "");
    }

    /**
     * Validates whether a provided option belongs to the command option set.
     *
     * @param cmd    command being executed
     * @param option option text to validate
     * @return {@code true} when option is empty or known for the command
     */
    private boolean validateOption(CommandObject cmd, String option) {
        if (option == null || option.isEmpty()) {
            return true;
        }
        Set<String> validOptions = new HashSet<>();
        if (cmd.commandOptions() != null) {
            for (OptionEntity spec : cmd.commandOptions()) {
                validOptions.add(spec.optionName());
            }
        }
        return validOptions.contains(option);
    }

    /**
     * Evaluates all command preconditions against current tool env state.
     *
     * @param cmd          command being executed
     * @param stateManager state manager for lookup
     * @param sessionId    current session
     * @param toolName     tool whose scoped state should be read
     * @return {@code true} if all preconditions pass
     */
    private boolean validatePreconditions(CommandObject cmd,
            SessionStateManager stateManager,
            String sessionId,
            String toolName) {
        if (cmd.commandPreConditions() == null || cmd.commandPreConditions().isEmpty()) {
            return true;
        }

        for (PreconditionObject precond : cmd.commandPreConditions()) {
            String currentValue = stateManager.getToolState(sessionId, toolName, precond.variable());
            if (!evaluateCondition(currentValue, precond.operator(), precond.value())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a single condition operator against current and expected values.
     *
     * @param currentValue  value read from current session state
     * @param operator      comparison operator
     * @param expectedValue precondition reference value
     * @return {@code true} if condition holds
     */
    private boolean evaluateCondition(String currentValue, ConditionOp operator, String expectedValue) {
        if (currentValue == null) {
            return switch (operator) {
                case NE -> expectedValue != null;
                case EQ -> expectedValue == null;
                default -> false;
            };
        }

        return switch (operator) {
            case EQ -> currentValue.equals(expectedValue);
            case NE -> !currentValue.equals(expectedValue);
            case GT -> parseAndCompare(currentValue, expectedValue, (a, b) -> a > b);
            case LT -> parseAndCompare(currentValue, expectedValue, (a, b) -> a < b);
            case GTE -> parseAndCompare(currentValue, expectedValue, (a, b) -> a >= b);
            case LTE -> parseAndCompare(currentValue, expectedValue, (a, b) -> a <= b);
        };
    }

    private boolean parseAndCompare(String currentValue, String expectedValue,
            java.util.function.BiPredicate<Double, Double> comparator) {
        try {
            return comparator.test(Double.parseDouble(currentValue), Double.parseDouble(expectedValue));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Result of command execution in the CLI simulator.
     *
     * @param success  whether execution succeeded
     * @param exitCode process-like exit code
     * @param stdout   simulated standard output
     * @param stderr   simulated standard error
     */
    public record ExecutionResult(boolean success, int exitCode, String stdout, String stderr) {
    }

    /**
     * Formats a state snapshot deterministically for stable log output.
     *
     * @param state tool-state snapshot
     * @return sorted map string representation
     */
    private String formatState(Map<String, String> state) {
        if (state == null || state.isEmpty()) {
            return "{}";
        }
        // Sort keys so repeated runs are diff-friendly.
        return new TreeMap<>(state).toString();
    }
}
