package org.benchmark.prompt;

import org.benchmark.exec.CommandOptionNormalizer;
import lombok.RequiredArgsConstructor;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds retry feedback from observable runtime evidence only.
 *
 * <p>The benchmark must not leak hidden answer-path data back into the model on retry.
 * Feedback therefore includes only the latest execution results, the current session
 * state, and a generic instruction to inspect documentation and state before trying
 * again.</p>
 */
@Component
@RequiredArgsConstructor
public class BenchmarkAttemptFeedbackBuilder {

    private final SessionStateManager stateManager;

    /**
     * Builds retry feedback without leaking the hidden scenario answer path back to the model.
     *
     * @param newExecutions executions produced during the latest attempt only
     * @param sessionId active benchmark session identifier
     * @param goalAchieved whether the latest attempt already satisfied the scenario
     * @return feedback text grounded in runtime evidence
     */
    public String build(List<ExecutionRecord> newExecutions,
                        String sessionId,
                        boolean goalAchieved) {
        String currentSessionState = String.valueOf(stateManager.getSessionStateSnapshot(sessionId));

        if (goalAchieved) {
            return "SUCCESS: The latest attempt produced a completed runtime state."
                    + "\nExecution results:\n"
                    + formatExecutionResults(newExecutions)
                    + "\nCurrent session state: " + currentSessionState;
        }

        if (newExecutions.isEmpty()) {
            return "INCOMPLETE: No tool command executed during the latest attempt."
                    + "\nCurrent session state: " + currentSessionState
                    + "\nNext step: Choose the next valid command from the tool documentation and call executeCommand exactly once.";
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append("INCOMPLETE: Review the latest execution results, the current session state,")
                .append(" and the tool documentation before the next attempt.");
        feedback.append("\nExecution results:\n").append(formatExecutionResults(newExecutions));
        feedback.append("\nCurrent session state: ").append(currentSessionState);
        feedback.append("\nNext step: Choose the next valid command from the tool documentation and call executeCommand exactly once.");
        return feedback.toString();
    }

    private String formatExecutionResults(List<ExecutionRecord> newExecutions) {
        StringBuilder executionSummary = new StringBuilder();
        for (ExecutionRecord record : newExecutions) {
            executionSummary.append("- result=")
                    .append(record.success() ? "SUCCESS" : "ERROR")
                    .append(", tool=").append(record.toolName())
                    .append(", command=").append(record.commandName())
                    .append(", option=").append(CommandOptionNormalizer.formatForModel(record.option()))
                    .append(", message=").append(record.message())
                    .append("\n");
        }
        return executionSummary.toString().trim();
    }
}
