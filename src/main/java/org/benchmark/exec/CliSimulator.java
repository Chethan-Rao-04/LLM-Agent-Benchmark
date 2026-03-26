package org.benchmark.exec;

import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.PreconditionObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal in-memory CLI simulator used by benchmark evaluation.
 *
 * <p>The simulator validates command preconditions and option values,
 * then applies command effects to session state.</p>
 */
public class CliSimulator {

    /**
     * Executes a command against session state.
     *
     * @param cmd command specification to execute
     * @param option chosen option (may be {@code null})
     * @param stateManager session state storage
     * @param sessionId session identifier
     * @param toolName tool whose environment is being executed against
     * @return execution status including exit code and output text
     */
    public ExecutionResult execute(CommandObject cmd,
                                   String option,
                                   SessionStateManager stateManager,
                                   String sessionId,
                                   String toolName) {
        Map<String, String> beforeState = new HashMap<>(stateManager.getToolStateSnapshot(sessionId, toolName));
        System.out.printf("[CLI] execute tool=%s cmd=%s option=%s%n", toolName, cmd.name(), option);

        if (!validatePreconditions(cmd, stateManager, sessionId, toolName)) {
            System.out.printf("[CLI] blocked by preconditions, state=%s%n", beforeState);
            return new ExecutionResult(false, 2, "", "Preconditions not met for command: " + cmd.name());
        }

        if (!validateOption(cmd, option)) {
            System.out.printf("[CLI] invalid option=%s for cmd=%s%n", option, cmd.name());
            return new ExecutionResult(false, 2, "", "Unknown option " + option + " for command " + cmd.name());
        }

        CommandEffectApplier.applyEffects(cmd.commandEffectObjects(), option, stateManager, sessionId, toolName);
        System.out.printf("[CLI] state before=%s%n", beforeState);
        System.out.printf("[CLI] state after=%s%n", stateManager.getToolStateSnapshot(sessionId, toolName));
        return new ExecutionResult(true, 0, "OK: " + cmd.name(), "");
    }

    /**
     * Validates whether a provided option belongs to the command option set.
     *
     * @param cmd command being executed
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
     * Evaluates all command preconditions against current session state.
     *
     * @param cmd command being executed
     * @param stateManager state manager for lookup
     * @param sessionId current session
     * @param toolName tool whose scoped state should be read
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
     * @param currentValue value read from current session state
     * @param operator comparison operator
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

        switch (operator) {
            case EQ:
                return currentValue.equals(expectedValue);
            case NE:
                return !currentValue.equals(expectedValue);
            case GT:
                try { return Double.parseDouble(currentValue) > Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            case LT:
                try { return Double.parseDouble(currentValue) < Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            case GTE:
                try { return Double.parseDouble(currentValue) >= Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            case LTE:
                try { return Double.parseDouble(currentValue) <= Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            default:
                return false;
        }
    }

    /**
     * Result of command execution in the CLI simulator.
     *
     * @param success whether execution succeeded
     * @param exitCode process-like exit code
     * @param stdout simulated standard output
     * @param stderr simulated standard error
     */
    public record ExecutionResult(boolean success, int exitCode, String stdout, String stderr) {
    }
}
