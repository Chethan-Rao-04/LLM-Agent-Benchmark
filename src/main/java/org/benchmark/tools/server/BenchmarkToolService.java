package org.benchmark.tools.server;

import org.benchmark.exec.SessionStateManager;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Stable tool callback facade for benchmark state inspection and execution.
 */
@Service
public class BenchmarkToolService {

    public enum CommandOutcomeType {
        EXECUTED,
        REJECTED
    }

    private final SessionStateManager stateManager;
    private final BenchmarkToolExecutionService executionService;

    /**
     * Creates the facade used by Spring AI tool callbacks.
     *
     * @param stateManager session-state access
     * @param executionService command execution service
     */
    public BenchmarkToolService(SessionStateManager stateManager,
                                BenchmarkToolExecutionService executionService) {
        this.stateManager = stateManager;
        this.executionService = executionService;
    }

    /**
     * Returns the current mutable state for all tools in the benchmark session.
     *
     * @param sessionId active benchmark session identifier
     * @return state snapshot response
     */
    public StateResponse getCurrentState(String sessionId) {
        stateManager.requireBenchmarkCase(sessionId);
        return new StateResponse(
                stateManager.getAllToolStatesSnapshot(sessionId),
                stateManager.getSharedStateSnapshot(sessionId)
        );
    }

    /**
     * Executes one command on a generated benchmark tool.
     *
     * @param sessionId active benchmark session identifier
     * @param toolName selected tool name
     * @param commandName selected command name
     * @param option selected command flag, or empty when the command declares no options
     * @return execution response with updated state snapshot
     */
    public CommandExecutionResponse executeCommand(String sessionId,
                                                   String toolName,
                                                   String commandName,
                                                   String option) {
        return executionService.executeCommand(sessionId, toolName, commandName, option);
    }

    /**
     * Response wrapper for session-state inspection.
     */
    public record StateResponse(Map<String, Map<String, String>> toolStates,
                                Map<String, String> sharedState) {
        public StateResponse(Map<String, Map<String, String>> toolStates) {
            this(toolStates, Map.of());
        }
    }
    /**
     * Response wrapper for benchmark tool execution results.
     */
    public record CommandExecutionResponse(CommandOutcomeType outcomeType,
                                           boolean success,
                                           String message,
                                           Map<String, Map<String, String>> toolStates,
                                           Map<String, String> sharedState) {
        public CommandExecutionResponse(CommandOutcomeType outcomeType,
                                        boolean success,
                                        String message,
                                        Map<String, Map<String, String>> toolStates) {
            this(outcomeType, success, message, toolStates, Map.of());
        }
    }
}
