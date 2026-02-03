package org.benchmark.task;

import org.benchmark.model.documentation.OptionSpec;
import org.benchmark.model.documentation.CommandObject;
import org.benchmark.model.documentation.CommandPreconditions;
import picocli.CommandLine;

import java.util.*;

public class DynamicCliExecutor {

    public Map<String, String> execute(CommandObject cmdObject,  Set<String> options,  StateRetentionManager stateManager, String sessionId) throws Exception {

        // TODO:  Validate preconditions first correctly
        if (!validatePreconditions(cmdObject, stateManager, sessionId)) {
            throw new IllegalStateException("Preconditions not met for command: " + cmdObject.commandName());
        }
        // validate whether the received options are part of the command
        Set<String> validOptions = new HashSet<>();
        for(OptionSpec spec: cmdObject.commandOptions()){
            validOptions.add(spec.optionName());
        }
        for(String providedOption : options){
            if(!validOptions.contains(providedOption)){
                throw new IllegalArgumentException("Unknown option "+ providedOption + " for the command " +  cmdObject.commandName() + "Pass the correct option");
            }
        }
        // 3. Return map showing which flags were present (ENABLED) or absent (DISABLED)
        Map<String, String> result = new HashMap<>();
        for (OptionSpec spec : cmdObject.commandOptions()) {
            result.put(spec.optionName(),
                    options.contains(spec.optionName()) ? "ENABLED" : "DISABLED");
        }

        return result;
    }

    private boolean validatePreconditions(CommandObject cmdObject, StateRetentionManager stateManager, String sessionId) {
        if (cmdObject.commandPreConditions() != null) {
            for (CommandPreconditions precond : cmdObject.commandPreConditions()) {
                String currentValue = stateManager.getState(sessionId, precond.variable());
                if (!evaluateCondition(currentValue, precond.operator(), precond.value())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean evaluateCondition(String currentValue, String operator, String expectedValue) {
        if (currentValue == null) return false;

        switch (operator) {
            case "==":
                return currentValue.equals(expectedValue);
            case "!=":
                return !currentValue.equals(expectedValue);
            case ">":
                try {
                    return Double.parseDouble(currentValue) > Double.parseDouble(expectedValue);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "<":
                try {
                    return Double.parseDouble(currentValue) < Double.parseDouble(expectedValue);
                } catch (NumberFormatException e) {
                    return false;
                }
            case ">=":
                try {
                    return Double.parseDouble(currentValue) >= Double.parseDouble(expectedValue);
                } catch (NumberFormatException e) {
                    return false;
                }
            case "<=":
                try {
                    return Double.parseDouble(currentValue) <= Double.parseDouble(expectedValue);
                } catch (NumberFormatException e) {
                    return false;
                }
            default:
                return false;
        }
    }
}