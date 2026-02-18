package org.benchmark.task;

import org.benchmark.model.tool.OptionSpec;
import org.benchmark.model.tool.CommandObject;
import org.benchmark.model.tool.CommandPreconditions;

import java.util.*;

public class DynamicCliExecutor {

    // TODO: THink how to make this clas actually simulate a command run and maybe change state variables (and if req satisfy preconditions via changing state)

    // this mainly validates the options provided by the LLM and
    //  checks whether they are part of the command specification, 
    // it also checks for the preconditions before executing the command. 
    public Boolean validateOptionsAndPrecond(CommandObject cmdObject,  String option,  StateRetentionManager stateManager, String sessionId) throws Exception {


        boolean check = false;
        // TODO:  Validate preconditions before executing command
        if (!validatePreconditions(cmdObject, stateManager, sessionId)) {
            throw new IllegalStateException("Preconditions not met for command: " + cmdObject.commandName());
        }
        
        // Add all options to a set for easy validation
        Set<String> validOptions = new HashSet<>();
        if (cmdObject.commandOptions() != null) {
            for(OptionSpec spec: cmdObject.commandOptions()){
                validOptions.add(spec.optionName());
            }
        }
       // validate whether the received option is  part of the command options set
        if(!validOptions.contains(option)){
                throw new IllegalArgumentException("Unknown option "+ option + " for the command " +  cmdObject.commandName() + ". Pass the correct option");
            }
        check = true;
    
        return check;
    }


    // helper methofd to validate preconditions
    private boolean validatePreconditions(CommandObject cmdObject, StateRetentionManager stateManager, String sessionId) {
        if (cmdObject.commandPreConditions() != null) {
            for (CommandPreconditions precond : cmdObject.commandPreConditions()) {
                String currentValue = stateManager.getState(sessionId, precond.variable());

                // If the variable isn't set yet, we treat it as null/missing.
                // Depending on the logic, a missing variable usually fails a comparison unless checking for != null
                if (!evaluateCondition(currentValue, precond.operator(), precond.value())) {
                    return false;
                }
            }
        }
        return true;
    }


    // Helper method to evaluate whether a state variable meets a precondition
    private boolean evaluateCondition(String currentValue, String operator, String expectedValue) {
        // If state is missing, we can't strictly evaluate numeric operators. 
        // For equality, null != "some_value" is true/valid.
        if (currentValue == null)  return operator.equals("!="); 

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