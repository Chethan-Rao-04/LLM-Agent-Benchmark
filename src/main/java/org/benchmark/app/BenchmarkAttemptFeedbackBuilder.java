package org.benchmark.app;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.scenario.ResolvedStep;
import org.benchmark.gen.tool_generator.CommandAbbreviator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds concise attempt feedback from execution results and expected scenario state.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkAttemptFeedbackBuilder {

    private final SessionStateManager stateManager;
    private final BenchmarkScorer scorer;

    public String build(List<ExecutionRecord> newExecutions,
                        String sessionId,
                        boolean goalAchieved,
                        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        if (goalAchieved) {
            return "SUCCESS: All scenario steps completed. Final target tool state: "
                    + targetToolState(sessionId, benchmarkCase);
        }

        if (newExecutions.isEmpty()) {
            return "ERROR: No tool execution occurred. Call listAvailableTools and getToolDocumentation first, then execute the correct tool.";
        }

        Map<String, String> actualToolState = targetToolState(sessionId, benchmarkCase);
        int stateAccuracyPercent = (int) (scorer.scoreExpectedState(
                benchmarkCase.expectedState(), actualToolState) * 100);

        String targetToolName = benchmarkCase.targetToolObject().name();
        boolean usedWrongTool = newExecutions.stream()
                .noneMatch(record -> record.toolName().equalsIgnoreCase(targetToolName));

        StringBuilder feedback = new StringBuilder();
        feedback.append("INCOMPLETE: Scenario not yet completed (state accuracy: ")
                .append(stateAccuracyPercent).append("%).");

        if (usedWrongTool) {
            int totalTools = benchmarkCase.allTools().size();
            int docsRead = stateManager.documentedTools(sessionId).size();
            feedback.append(" WARNING: None of the tools you executed are the correct target tool.")
                    .append(" You have read documentation for ").append(docsRead)
                    .append(" of ").append(totalTools).append(" available tools.")
                    .append(" Call getToolDocumentation for the tools you haven't read yet.");
        } else {
            appendStepGuidance(feedback, newExecutions, sessionId, benchmarkCase, targetToolName);
        }

        for (ExecutionRecord record : newExecutions) {
            feedback.append("\n- ")
                    .append(record.success() ? "SUCCESS" : "ERROR")
                    .append(" tool=").append(record.toolName())
                    .append(", command=").append(record.commandName())
                    .append(", option=").append(record.option())
                    .append(", message=").append(record.message());
        }
        feedback.append("\nTarget tool state: ").append(actualToolState);
        return feedback.toString();
    }

    private void appendStepGuidance(StringBuilder feedback,
                                    List<ExecutionRecord> newExecutions,
                                    String sessionId,
                                    BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                    String targetToolName) {
        List<ExecutionRecord> allLogs = stateManager.executionLog(sessionId);
        List<ResolvedStep> steps = benchmarkCase.targetSteps();
        List<String> requiredCommands = steps.stream()
                .map(step -> CommandAbbreviator.commandName(step.verb(), step.noun()))
                .toList();
        int completedSteps = 0;
        String nextStep = null;
        String nextPrecondition = null;

        for (ResolvedStep step : steps) {
            String abbreviated = CommandAbbreviator.commandName(step.verb(), step.noun());
            boolean done = allLogs.stream().anyMatch(record ->
                    record.success()
                            && record.toolName().equalsIgnoreCase(targetToolName)
                            && record.commandName().equalsIgnoreCase(abbreviated));
            if (done) {
                completedSteps++;
            } else if (nextStep == null) {
                nextStep = abbreviated;
                nextPrecondition = step.precondition().isEmpty() ? null : step.precondition().toString();
            }
        }

        feedback.append(" Progress: ").append(completedSteps).append("/").append(steps.size()).append(" steps done.");
        if (nextStep != null) {
            feedback.append(" Next required: ").append(nextStep).append(".");
            if (nextPrecondition != null) {
                feedback.append(" Precondition: ").append(nextPrecondition).append(".");
            }
        }

        List<String> extraTargetCommands = newExecutions.stream()
                .filter(record -> record.toolName().equalsIgnoreCase(targetToolName))
                .map(ExecutionRecord::commandName)
                .filter(command -> requiredCommands.stream().noneMatch(required -> required.equalsIgnoreCase(command)))
                .distinct()
                .toList();
        if (!extraTargetCommands.isEmpty()) {
            feedback.append(" WARNING: You called commands on ").append(targetToolName)
                    .append(" that are not part of the required scenario: ")
                    .append(extraTargetCommands)
                    .append(". Only execute the steps needed to achieve the goal.");
        }

        List<String> wrongToolsUsed = newExecutions.stream()
                .map(ExecutionRecord::toolName)
                .filter(toolName -> !toolName.equalsIgnoreCase(targetToolName))
                .distinct()
                .toList();
        if (!wrongToolsUsed.isEmpty()) {
            feedback.append(" WARNING: You also executed commands on non-target tools: ")
                    .append(wrongToolsUsed)
                    .append(". These tools are not needed for this task. Do not execute commands on tools that are not the target.");
        }
    }

    private Map<String, String> targetToolState(String sessionId,
                                                BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name());
    }
}
