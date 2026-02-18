package org.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.model.tool.CommandObject;
import org.benchmark.model.tool.CommandEffect;
import org.benchmark.model.tool.CommandPreconditions;
import org.benchmark.llm.LangChainClient;
import org.benchmark.llm.ResponseParser;
import org.benchmark.llm.ResponseParser.ParsedToolCall;
import org.benchmark.model.tool.ToolComplexity;
import org.benchmark.task.DynamicCliExecutor;
import org.benchmark.task.StateRetentionManager;
import org.benchmark.factory.ToolKitFactory;
import org.benchmark.utils.BenchmarkLogger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class BenchmarkEngine {

    private final String modelName = "llama3";
    private final int iterations = 10;
    private final ToolComplexity toolComplexity = ToolComplexity.MEDIUM;

    private final StateRetentionManager stateManager = new StateRetentionManager();
    private final DynamicCliExecutor cliExecutor = new DynamicCliExecutor();
    private final BenchmarkLogger logger = new BenchmarkLogger("benchmark_results_autonomous.csv");

    private static final int MAX_RETRIES = 3;

    private static final String BASE_SYSTEM_PROMPT =
            "You are an autonomous agent. Select the correct tool from the provided set of tools to solve the user query.\n" +
                    "CRITICAL: You must respond with ONLY valid JSON. Do not include any explanatory text, comments, or formatting.\n" +
                    "Output format: {\"tool\": \"<tool_name>\", \"command\": \"<command_name>\", \"option\": \"<value>\"}\n" +
                    "Example output: {\"tool\": \"MAN-ROUTER-456\", \"command\": \"reset_router\", \"option\": \"--verbose\"}\n" +
                    "Remember: Your entire response must be valid JSON only.\n";

    public static void main(String[] args) {
        BenchmarkEngine engine = new BenchmarkEngine();
        engine.runBenchmark();
    }

    public void runBenchmark() {
        LangChainClient client = new LangChainClient(modelName, "http://localhost:11434");
        ToolKitFactory generator = new ToolKitFactory();
        System.out.println("Generating Autonomous Execution Benchmark..");

        var toolKit = generator.generateToolKit(toolComplexity, iterations, 2, null);
        int totalSuccess = 0;
        int autonomousRecoveries = 0;

        for (int i = 0; i < toolKit.size(); i++) {
            var toolKitInst = toolKit.get(i);
            String sessionId = UUID.randomUUID().toString();

            // Initialize default state
            // TODO- add more states like "SHUTDOWN" so the llm has to change it to "READY" using the correct command before executing the target command
            stateManager.updateState(sessionId, "system_status", "READY");

            String userGoal = toolKitInst.generateUserQuery(toolKitInst.targetToolObject());
            CommandObject targetCmd = toolKitInst.targetCommand();
            String targetOptionName = toolKitInst.targetOptionName();

            // --- BEGIN-Log Ground Truth & Expectation ---
            System.out.println("\n============================================================");
            System.out.printf(" Run %d: %s %n", i + 1, userGoal);
            System.out.println("------------------------------------------------------------");
            System.out.println("   [TARGET EXPECTATION]");
            System.out.printf("     Tool:          %s%n", toolKitInst.targetToolObject().name());
            System.out.printf("     Command:       %s%n", targetCmd.commandName());

            if (targetCmd.commandOptions() != null && !targetCmd.commandOptions().isEmpty()) {
                System.out.print("     Available Options: [ ");
                targetCmd.commandOptions().forEach(opt -> System.out.print(opt.optionName() + " "));
                System.out.println("]");
            } else {
                System.out.println("     Available Options: [ None ]");
            }

            if(!targetOptionName.isEmpty()){
                System.out.println("     Target Option:  " + targetOptionName);
            }else {
                System.out.println("    Target Option is null");
            }

            if (targetCmd.commandPreConditions() != null && !targetCmd.commandPreConditions().isEmpty()) {
                System.out.println("     Preconditions: ");
                for (CommandPreconditions pc : targetCmd.commandPreConditions()) {
                    System.out.printf("       * %s %s %s%n", pc.variable(), pc.operator(), pc.value());
                }
            } else {
                System.out.println("     Preconditions: [ None ]");
            }
            System.out.println("============================================================\n");
            // --- END-Log Ground Truth & Expectation ---


            // Prepare for loop
            String conversationHistory = "";
            boolean goalAchieved = false;
            int attempt = 0;
            long totalTime = 0;
            int totalTokens = 0;
            boolean toolMatch = false;

            while (attempt < MAX_RETRIES && !goalAchieved) {
                attempt++;

                String stateContext = "\n[CURRENT STATE]: " + stateManager.getAllStates(sessionId);
                String historyContext = "\n[HISTORY]:" + conversationHistory;
                String fullPrompt = BASE_SYSTEM_PROMPT + toolKitInst.combinedToolDesc() + stateContext + historyContext;

                //start time 
                long tStart = System.nanoTime();

                // Execute LLM call with the full prompt and user goal, get response and token usage
                var result = client.LlmExecute(fullPrompt, userGoal);

                // Calculate time taken for execution
                long tLat = (System.nanoTime() - tStart) / 1_000_000;
                totalTime += tLat;

                // get token usage from the result 
                totalTokens += result.tokenUsage();


                // Log Raw Response
                System.out.println("   [Raw LLM Response]: " + result.content());

                // parse raw llm response
                ResponseParser parser = new ResponseParser();
                ParsedToolCall parsed = parser.parse(result.content());
                // Initialize stepOutput to avoid compilation errors
                String stepOutput = "ERROR: Unknown execution failure";

                if (parsed.isHasToolAndCmdName()) {
                    try {
                        CommandObject actualCommandObject = findCommandObjectByName(parsed.getCommandName(), toolKitInst);

                        if (actualCommandObject == null) {
                            throw new IllegalArgumentException("Unknown command '" + parsed.getCommandName() + "'. Please check documentation.");
                        }

                        // 2. Validate Preconditions
                        if (!validatePreconditions(actualCommandObject, stateManager, sessionId)) {
                            stepOutput = "ERROR: Preconditions failed for " + parsed.getCommandName();
                            // Update history here before continuing or let it fall through (using fall-through logic below)
                        } else {
                            // 3. Execution & Evaluation of Identity
                            String actualToolName = toolKitInst.targetToolObject().name();
                            String actualCommand = toolKitInst.targetCommand().commandName();

                            boolean correctTool = parsed.getToolName().equalsIgnoreCase(actualToolName);
                            boolean correctCommand = parsed.getCommandName().equalsIgnoreCase(actualCommand);

                            String predictedOption = parsed.getOption();
                           // System.out.println("   [Parsed option (LLM Response)]: " + predictedOption);

                            if (!correctTool) {
                                // Fix: Explicitly report tool mismatch
                                stepOutput = "ERROR: Tool Mismatch. Executed on '" + parsed.getToolName() + " but expected another tool" ;
                            } else if (!correctCommand) {
                                stepOutput = "ERROR: Command Mismatch. Executed '" + parsed.getCommandName() + " but expected another command";
                            } else {

                                // Tool & Command are correct, now check Option
                                String expectedOpt =  toolKitInst.targetOptionName().trim();
                                System.out.println("Expected option after trim " + expectedOpt);

                                String receivedOpt = predictedOption == null ? "" : predictedOption;
                                System.out.println("Predicted option after trim " + predictedOption);
                                boolean correctOption = receivedOpt.equalsIgnoreCase(expectedOpt);
                                if (!correctOption) {
                                    stepOutput = "ERROR: Option Mismatch. Expected: " + toolKitInst.targetOptionName() +
                                            ", but received: " + predictedOption;
                                } else {
                                    // All checks passed
                                    applyCommandEffects(actualCommandObject, stateManager, sessionId);

                                    goalAchieved = true;
                                    toolMatch = true;
                                    stepOutput = "\nSUCCESS: executed " + parsed.getToolName() + ":" +
                                            parsed.getCommandName() + " with option " + predictedOption;
                                }
                            }
                        }

                    } catch (Exception e) {
                        stepOutput = "ERROR: " + e.getMessage();
                    }
                } else {
                    stepOutput = "ERROR: Invalid JSON format. Please output valid JSON. No additional Text";
                }

                // Update history and Log (Consolidated at the bottom to ensure stepOutput is always used)
                conversationHistory += "\nAssistant: called " + parsed.getToolName() + ":" + parsed.getCommandName();
                conversationHistory += "\nSystem: " + stepOutput;

                System.out.printf("  Attempt %d (%dms): %s:%s -> %s%n", attempt, tLat, parsed.getToolName(), parsed.getCommandName(), stepOutput);
            }
            // Calculate final state score
            double finalStateScore = goalAchieved ?
                    validateStateTransition(toolKitInst.expectedState(), stateManager.getAllStates(sessionId)) : 0.0;

            logger.log(modelName, toolComplexity.name(), 0, totalTime, totalTokens,
                    toolMatch, finalStateScore, goalAchieved);
            stateManager.clearSession(sessionId);

            if (goalAchieved) {
                totalSuccess++;
                if (attempt > 1) autonomousRecoveries++;
            }
            else {
                System.out.println("   [FAILURE]: Unable to achieve goal after " + MAX_RETRIES + " attempts.");
            }
        }

        System.out.println("\nFinal Success Rate: " + totalSuccess + "/" + iterations);
        System.out.println("Autonomous Recoveries: " + autonomousRecoveries);
    }
  // TODO- implement correctly
    private void applyCommandEffects(CommandObject cmdObj, StateRetentionManager stateManager, String sessionId) {
        if (cmdObj.commandEffects() != null) {
            for (CommandEffect effect : cmdObj.commandEffects()) {
                switch (effect.operation().toUpperCase()) {
                    case "ASSIGN":
                        stateManager.updateState(sessionId, effect.variable(), effect.valueRef());
                        break;
                    case "INCREMENT":
                        String currentVal = stateManager.getState(sessionId, effect.variable());
                        int current = currentVal != null ? Integer.parseInt(currentVal) : 0;
                        stateManager.updateState(sessionId, effect.variable(), String.valueOf(current + 1));
                        break;
                    case "DECREMENT":
                        String currentDec = stateManager.getState(sessionId, effect.variable());
                        int currentDecVal = currentDec != null ? Integer.parseInt(currentDec) : 0;
                        stateManager.updateState(sessionId, effect.variable(), String.valueOf(currentDecVal - 1));
                        break;
                    case "DELETE":
                        stateManager.updateState(sessionId, effect.variable(), null);
                        break;
                }
            }
        }
    }
  // TODO- implement correctly
    private double validateStateTransition(Map<String, String> expectedState, Map<String, String> actualState) {
        if (expectedState.isEmpty()) return 1.0;

        int matches = 0;
        for (Map.Entry<String, String> expected : expectedState.entrySet()) {
            String actualValue = actualState.get(expected.getKey());
            if (expected.getValue() != null && expected.getValue().equals(actualValue)) {
                matches++;
            }
        }
        return (double) matches / expectedState.size();
    }


    // Helper method
    private CommandObject findCommandObjectByName(String commandName, ToolKitFactory.ToolKitObjectModel inst) {
        if (inst.targetCommand().commandName().equalsIgnoreCase(commandName)) {
            return inst.targetCommand();
        }
        for (CommandObject cmd : inst.targetToolObject().commands()) {
            if (cmd.commandName().equalsIgnoreCase(commandName)) {
                return cmd;
            }
        }
        for (var distractor : inst.distractors()) {
            for (CommandObject cmd : distractor.commands()) {
                if (cmd.commandName().equalsIgnoreCase(commandName)) {
                    return cmd;
                }
            }
        }
        return null; // Not found
    }

    // TODO- implement correctly
    private boolean validatePreconditions(CommandObject cmd, StateRetentionManager stateManager, String sessionId) {
        if (cmd.commandPreConditions() == null || cmd.commandPreConditions().isEmpty()) {
            return true;
        }

        for (CommandPreconditions precond : cmd.commandPreConditions()) {
            String variable = precond.variable();
            String operator = precond.operator();
            String expectedValue = precond.value();

            String currentValue = stateManager.getState(sessionId, variable);

            if (!evaluateCondition(currentValue, operator, expectedValue)) {
                // System.out.printf("   [Precondition Fail] %s: Expected %s '%s', Found '%s'%n", variable, operator, expectedValue, (currentValue == null ? "null" : currentValue));
                return false;
            }
        }
        return true;
    }
    // TODO- implement correctly
    private boolean evaluateCondition(String currentValue, String operator, String expectedValue) {
        if (currentValue == null) return operator.equals("!=");

        switch (operator) {
            case "==":
                return currentValue.equals(expectedValue);
            case "!=":
                return !currentValue.equals(expectedValue);
            case ">":
                try { return Double.parseDouble(currentValue) > Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            case "<":
                try { return Double.parseDouble(currentValue) < Double.parseDouble(expectedValue); }
                catch (NumberFormatException e) { return false; }
            default:
                return false;
        }
    }
}