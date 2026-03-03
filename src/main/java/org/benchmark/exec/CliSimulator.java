package org.benchmark.exec;

import org.benchmark.model.enums.ConditionOp;
import org.benchmark.model.spec.CommandSpec;
import org.benchmark.model.spec.OptionSpec;
import org.benchmark.model.spec.Precondition;

import java.util.HashSet;
import java.util.Set;

public class CliSimulator {

    public ExecutionResult execute(CommandSpec cmd, String option, SessionStateManager stateManager, String sessionId) {
        if (!validatePreconditions(cmd, stateManager, sessionId)) {
            return new ExecutionResult(false, 2, "", "Preconditions not met for command: " + cmd.commandName());
        }

        if (!validateOption(cmd, option)) {
            return new ExecutionResult(false, 2, "", "Unknown option " + option + " for command " + cmd.commandName());
        }

        CommandEffectApplier.applyEffects(cmd.commandEffects(), option, stateManager, sessionId);
        return new ExecutionResult(true, 0, "OK: " + cmd.commandName(), "");
    }

    private boolean validateOption(CommandSpec cmd, String option) {
        if (option == null || option.isEmpty()) {
            return true;
        }
        Set<String> validOptions = new HashSet<>();
        if (cmd.commandOptions() != null) {
            for (OptionSpec spec : cmd.commandOptions()) {
                validOptions.add(spec.optionName());
            }
        }
        return validOptions.contains(option);
    }

    private boolean validatePreconditions(CommandSpec cmd, SessionStateManager stateManager, String sessionId) {
        if (cmd.commandPreConditions() == null || cmd.commandPreConditions().isEmpty()) {
            return true;
        }

        for (Precondition precond : cmd.commandPreConditions()) {
            String currentValue = stateManager.getState(sessionId, precond.variable());
            if (!evaluateCondition(currentValue, precond.operator(), precond.value())) {
                return false;
            }
        }
        return true;
    }

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

    public record ExecutionResult(boolean success, int exitCode, String stdout, String stderr) {
    }
}
