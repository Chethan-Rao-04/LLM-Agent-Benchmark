package org.benchmark.app;

import org.benchmark.model.spec.CommandSpec;
import org.benchmark.model.spec.Precondition;
import org.benchmark.model.spec.ToolSpec;
import org.benchmark.llm.LlmClient;
import org.benchmark.llm.ToolCallParser;
import org.benchmark.llm.ToolCallParser.ParsedToolCall;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.ToolSpecGenerator;
import org.benchmark.utils.CsvBenchmarkLogger;

import java.util.Map;
import java.util.UUID;

public class BenchmarkRunner {

    private final String modelName = "llama3";
    private final int iterations = 10;
    private final SessionStateManager stateManager = new SessionStateManager();
    private final CliSimulator cliSimulator = new CliSimulator();
    private final CsvBenchmarkLogger logger = new CsvBenchmarkLogger("benchmark_results_autonomous.csv");

    private static final int MAX_RETRIES = 3;

    private static final String BASE_SYSTEM_PROMPT =
            "You are an autonomous agent. Select the correct tool from the provided set of tools to solve the user query.\n" +
                    "CRITICAL: You must respond with ONLY valid JSON. Do not include any explanatory text, comments, or formatting.\n" +
                    "Output format: {\"tool\": \"<tool_name>\", \"command\": \"<command_name>\", \"option\": \"<value>\"}\n" +
                    "Example output: {\"tool\": \"MAN-ROUTER-456\", \"command\": \"reset_router\", \"option\": \"--verbose\"}\n" +
                    "Remember: Your entire response must be valid JSON only.\n";

    public static void main(String[] args) {
        BenchmarkRunner runner = new BenchmarkRunner();
        runner.runBenchmark();
    }

    public void runBenchmark() {
        // Init components for this benchmark run.
        LlmClient client = new LlmClient(modelName);
        BenchmarkCaseGenerator caseGenerator = new BenchmarkCaseGenerator();
        System.out.println("Generating Autonomous Execution Benchmark..");

        // List of cases, Each case = one target tool/command (+ distractors) + expected state delta.
        var benchmarkCases = caseGenerator.generateCases(iterations, 2, null);
        int totalSuccess = 0;
        int autonomousRecoveries = 0;

        for (int i = 0; i < benchmarkCases.size(); i++) {
            var benchmarkCase = benchmarkCases.get(i);
            String sessionId = UUID.randomUUID().toString();

            // Start in SHUTDOWN so the model needs to run initialize_system before target command.
            stateManager.updateState(sessionId, ToolSpecGenerator.CONTROL_STATE_KEY, ToolSpecGenerator.CONTROL_STATE_SHUTDOWN);

            String userQuery = benchmarkCase.generateUserQuery(benchmarkCase.targetToolObject());
            CommandSpec targetCmd = benchmarkCase.targetCommand();
            String targetOptionName = benchmarkCase.targetOptionName();

            // --- BEGIN-Log Ground Truth & Expectation ---
            System.out.println("\n============================================================");
            System.out.printf(" Run %d: %s %n", i + 1, userQuery);
            System.out.println("------------------------------------------------------------");
            System.out.println("   [TARGET EXPECTATION]");
            System.out.printf("     Tool:          %s%n", benchmarkCase.targetToolObject().name());
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
                for (Precondition pc : targetCmd.commandPreConditions()) {
                    System.out.printf("       * %s %s %s%n", pc.variable(), pc.operator(), pc.value());
                }
            } else {
                System.out.println("     Preconditions: [ None ]");
            }
            System.out.println("============================================================\n");
            // --- END-Log Ground Truth & Expectation ---


            // Per-case execution stats.
            String conversationHistory = "";
            boolean goalAchieved = false;
            int attempt = 0;
            long totalTime = 0;
            int totalTokens = 0;
            boolean toolMatch = false;

            // Autonomous retry loop: send feedback from each failed attempt back to the model.
            while (attempt < MAX_RETRIES && !goalAchieved) {
                attempt++;

                String stateContext = "\n[CURRENT STATE]: " + stateManager.getAllStates(sessionId);
                String historyContext = "\n[HISTORY]:" + conversationHistory;
                String systemInstruction = BASE_SYSTEM_PROMPT + benchmarkCase.combinedToolDesc() + stateContext + historyContext;

                //start time 
                long tStart = System.nanoTime();

                // Execute LLM call with the system instruction as well as the User query, get response and token usage
                var result = client.execute(systemInstruction, userQuery);

                // Calculate time taken for execution
                long tLat = (System.nanoTime() - tStart) / 1_000_000;
                totalTime += tLat;

                // get token usage from the result 
                totalTokens += result.tokenUsage();


                // Log Raw Response
                System.out.println("   [Raw LLM Response]: " + result.content());

                // parse raw llm response
                ToolCallParser parser = new ToolCallParser();
                ParsedToolCall parsed = parser.parse(result.content());
                // Initialize stepOutput to avoid compilation errors
                String stepOutput = "ERROR: Unknown execution failure";

                if (parsed.hasToolAndCmdName()) {
                    try {
                        // Resolve command by (tool, command), not command-only, to avoid cross-tool collisions.
                        CommandSpec actualCommandSpec =
                                findCommandSpecForTool(parsed.toolName(), parsed.commandName(), benchmarkCase);
                        if (actualCommandSpec == null) {
                            if (!toolExists(parsed.toolName(), benchmarkCase)) {
                                throw new IllegalArgumentException("Unknown tool '" + parsed.toolName() + "'. Please check documentation.");
                            }
                            throw new IllegalArgumentException(
                                    "Unknown command '" + parsed.commandName() + "' for tool '" + parsed.toolName() + "'.");
                        }

                        String actualToolName = benchmarkCase.targetToolObject().name();
                        String actualCommand = benchmarkCase.targetCommand().commandName();

                        boolean correctTool = parsed.toolName().equalsIgnoreCase(actualToolName);
                        boolean prepCommand = actualCommandSpec.commandName().equalsIgnoreCase(ToolSpecGenerator.PREP_COMMAND_NAME);
                        String predictedOption = parsed.option();

                        if (prepCommand) {
                            // Prep command is allowed as an intermediate step before target command.
                            if (!correctTool) {
                                stepOutput = "ERROR: Tool Mismatch. Executed on '" + parsed.toolName() + " but expected another tool";
                            } else {
                                CliSimulator.ExecutionResult execResult =
                                        cliSimulator.execute(actualCommandSpec, predictedOption, stateManager, sessionId);
                                stepOutput = execResult.success()
                                        ? "INFO: prep command executed (" + ToolSpecGenerator.PREP_COMMAND_NAME + "). Now retry target command."
                                        : "ERROR: " + execResult.stderr();
                            }
                        } else {
                            boolean correctCommand = parsed.commandName().equalsIgnoreCase(actualCommand);
                            if (!correctTool) {
                                stepOutput = "ERROR: Tool Mismatch. Executed on '" + parsed.toolName() + " but expected another tool";
                            } else if (!correctCommand) {
                                stepOutput = "ERROR: Command Mismatch. Executed '" + parsed.commandName() + " but expected another command";
                            } else {
                                // Benchmark checks exact target option to keep scoring deterministic.
                                String expectedOpt = benchmarkCase.targetOptionName().trim();
                                String receivedOpt = predictedOption == null ? "" : predictedOption;
                                boolean correctOption = receivedOpt.equalsIgnoreCase(expectedOpt);

                                if (!correctOption) {
                                    stepOutput = "ERROR: Option Mismatch. Expected: " + benchmarkCase.targetOptionName() +
                                            ", but received: " + predictedOption;
                                } else {
                                    CliSimulator.ExecutionResult execResult =
                                            cliSimulator.execute(actualCommandSpec, predictedOption, stateManager, sessionId);
                                    if (!execResult.success()) {
                                        stepOutput = "ERROR: " + execResult.stderr();
                                    } else {
                                        goalAchieved = true;
                                        toolMatch = true;
                                        stepOutput = "\nSUCCESS: executed " + parsed.toolName() + ":" +
                                                parsed.commandName() + " with option " + predictedOption;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        stepOutput = "ERROR: " + e.getMessage();
                    }
                } else {
                    stepOutput = "ERROR: Invalid JSON format. Please output valid JSON. No additional Text";
                }

                // Persist structured feedback into history so the next retry can self-correct.
                String historyTool = parsed.toolName() == null ? "UNKNOWN_TOOL" : parsed.toolName();
                String historyCommand = parsed.commandName() == null ? "UNKNOWN_COMMAND" : parsed.commandName();
                conversationHistory += "\nAssistant: called " + historyTool + ":" + historyCommand;
                conversationHistory += "\nSystem: " + stepOutput;

                System.out.printf("  Attempt %d (%dms): %s:%s -> %s%n", attempt, tLat, parsed.toolName(), parsed.commandName(), stepOutput);
            }
            // State score is only computed on success; failed runs receive 0.
            double finalStateScore = goalAchieved ?
                    scoreExpectedState(benchmarkCase.expectedState(), stateManager.getAllStates(sessionId)) : 0.0;

            logger.log(modelName, 0, totalTime, totalTokens,
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

    // Scores only keys touched by command effects (expected state delta), not full session state.
    private double scoreExpectedState(Map<String, String> expectedState, Map<String, String> actualState) {
        if (expectedState.isEmpty()) return 1.0;

        int matches = 0;
        for (Map.Entry<String, String> expected : expectedState.entrySet()) {
            String actualValue = actualState.get(expected.getKey());
            String expectedValue = expected.getValue();
            if (expectedValue == null) {
                // DELETE semantics: expected to be missing or null
                if (actualValue == null) {
                    matches++;
                }
                continue;
            }
            if (expectedValue.equals(actualValue)) matches++;
        }
        return (double) matches / expectedState.size();
    }

    // Checks whether the model-selected tool exists among target + distractors in this case.
    private boolean toolExists(String toolName, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if (benchmarkCase.targetToolObject().name().equalsIgnoreCase(toolName)) {
            return true;
        }
        for (ToolSpec distractor : benchmarkCase.distractors()) {
            if (distractor.name().equalsIgnoreCase(toolName)) {
                return true;
            }
        }
        return false;
    }

    // Finds a command in the selected tool only (prevents false positives across tools).
    private CommandSpec findCommandSpecForTool(String toolName, String commandName,
                                               BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (toolName == null || commandName == null) {
            return null;
        }

        ToolSpec selectedTool = null;
        if (benchmarkCase.targetToolObject().name().equalsIgnoreCase(toolName)) {
            selectedTool = benchmarkCase.targetToolObject();
        } else {
            for (ToolSpec distractor : benchmarkCase.distractors()) {
                if (distractor.name().equalsIgnoreCase(toolName)) {
                    selectedTool = distractor;
                    break;
                }
            }
        }

        if (selectedTool == null) {
            return null;
        }

        for (CommandSpec cmd : selectedTool.commands()) {
            if (cmd.commandName().equalsIgnoreCase(commandName)) {
                return cmd;
            }
        }
        return null;
    }

}
