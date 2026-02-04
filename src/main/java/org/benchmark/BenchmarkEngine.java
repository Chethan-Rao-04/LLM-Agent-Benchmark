package org.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.model.documentation.CommandObject;
import org.benchmark.model.documentation.CommandEffect;
import org.benchmark.model.documentation.CommandPreconditions;
import org.benchmark.model.documentation.OptionSpec;
import org.benchmark.evaluation.LangChainClient;
import org.benchmark.evaluation.ResponseParser;
import org.benchmark.evaluation.ResponseParser.ParsedToolCall;
import org.benchmark.model.tool.ToolComplexity;
import org.benchmark.task.DynamicCliExecutor;
import org.benchmark.task.StateRetentionManager;
import org.benchmark.utils.BenchmarkLogger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class BenchmarkEngine {

    private final String modelName = "llama3";
    private final int iterations = 5;
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
        ToolKitGenerator generator = new ToolKitGenerator();
        System.out.println("Generating Autonomous Execution Benchmark..");

        var toolKit = generator.generateToolKit(toolComplexity, iterations, 2, null);

        int totalSuccess = 0;
        int autonomousRecoveries = 0;

        for (int i = 0; i < toolKit.size(); i++) {
            var toolKitInst = toolKit.get(i);
            String sessionId = UUID.randomUUID().toString();

            // Initialize default state
            stateManager.updateState(sessionId, "system_status", "READY");

            String userGoal = toolKitInst.generateUserQuery(toolKitInst.targetToolObject());
            CommandObject targetCmd = toolKitInst.targetCommand();

            // --- Log Ground Truth & Expectation ---
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

            if (targetCmd.commandPreConditions() != null && !targetCmd.commandPreConditions().isEmpty()) {
                System.out.println("     Preconditions: ");
                for (CommandPreconditions pc : targetCmd.commandPreConditions()) {
                    System.out.printf("       * %s %s %s%n", pc.variable(), pc.operator(), pc.value());
                }
            } else {
                System.out.println("     Preconditions: [ None ]");
            }
            System.out.println("============================================================\n");


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

                long tStart = System.nanoTime();
                var result = client.LlmExecute(fullPrompt, userGoal);

                long tLat = (System.nanoTime() - tStart) / 1_000_000;
                totalTime += tLat;
                totalTokens += result.tokenUsage();

                ResponseParser parser = new ResponseParser();
                ParsedToolCall parsed = parser.parse(result.content());
                String stepOutput;

                if (parsed.isHasToolAndCmdName()) {
                    try {
                        // 1. Resolve Command Object (Handle Unknown Commands Safely)
                        CommandObject actualCommandObject = findCommandObjectByName(parsed.getCommandName(), toolKitInst);

                        if (actualCommandObject == null) {
                            throw new IllegalArgumentException("Unknown command '" + parsed.getCommandName() + "'. Please check documentation.");
                        }

                        // 2. Validate Preconditions
                        if (!validatePreconditions(actualCommandObject, stateManager, sessionId)) {
                            stepOutput = "ERROR: Preconditions failed for " + parsed.getCommandName();

                            // Log hint to history
                            conversationHistory += "\nAssistant: called " + parsed.getToolName() + ":" + parsed.getCommandName();
                            conversationHistory += "\nSystem: " + stepOutput;
                            conversationHistory += "\n[HINT] Review the 'Preconditions' section in the tool documentation.";

                            System.out.printf("  Attempt %d (%dms): %s:%s -> %s%n",
                                    attempt, tLat, parsed.getToolName(), parsed.getCommandName(), stepOutput);

                            continue; // Skip execution
                        } else {
                            // System.out.println("   [Precondition Check] PASSED");
                        }

                        // 3. Execution & Scoring
                        String actualToolName = toolKitInst.targetToolObject().name();
                        String actualCommand = toolKitInst.targetCommand().commandName();

                        boolean correctTool = parsed.getToolName().equalsIgnoreCase(actualToolName);
                        boolean correctCommand = parsed.getCommandName().equalsIgnoreCase(actualCommand);

                        // Capture Parsed Options from LLM
                        Set<String> providedFlags = parsed.getFlags();

                        // Execute Command (Validates if provided options exist in the specific command)
                        Map<String, String> validParams = cliExecutor.execute(actualCommandObject, providedFlags, stateManager, sessionId);

                        // Update State based on enabled flags
                        for (Map.Entry<String, String> entry : validParams.entrySet()) {
                            stateManager.updateState(sessionId, entry.getKey().replace("--", ""), entry.getValue());
                        }

                        applyCommandEffects(actualCommandObject, stateManager, sessionId);

                        // Provide visual feedback on options selected
                        String optionsStr = providedFlags.isEmpty() ? "[None]" : providedFlags.toString();
                        stepOutput = "SUCCESS: executed " + parsed.getToolName() + ":" + parsed.getCommandName() + " with options " + optionsStr;

                        if (correctTool && correctCommand) {
                            double stateScore = validateStateTransition(toolKitInst.expectedState(), stateManager.getAllStates(sessionId));
                            if (stateScore >= 0.8) {
                                goalAchieved = true;
                                toolMatch = true;
                            } else {
                                stepOutput += " (State score: " + String.format("%.2f", stateScore) + ")";
                            }
                        } else {
                            stepOutput += " (Wrong tool/command selected)";
                        }
                    } catch (Exception e) {
                        stepOutput = "ERROR: " + e.getMessage();
                        stateManager.incrementRetryCount(sessionId, parsed.getCommandName());
                    }
                } else {
                    stepOutput = "ERROR: Invalid JSON format. Please output valid JSON.";
                }

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
        }

        System.out.println("\nFinal Success Rate: " + totalSuccess + "/" + iterations);
        System.out.println("Autonomous Recoveries: " + autonomousRecoveries);
    }

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

    private CommandObject findCommandObjectByName(String commandName, ToolKitGenerator.ToolKitObjectModel inst) {
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

    private boolean evaluateCondition(String currentValue, String operator, String expectedValue) {
        if (currentValue == null) return false;

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