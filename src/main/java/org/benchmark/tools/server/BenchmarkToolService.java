package org.benchmark.tools.server;

import lombok.extern.slf4j.Slf4j;
import org.benchmark.app.BenchmarkEventLogger;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.app.BenchmarkScorer;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Benchmark tool service providing discovery and execution operations.
 *
 * <p>Discovery methods (listAvailableTools, getToolDocumentation, getCurrentState)
 * are exposed to the LLM as direct FunctionToolCallbacks via the
 * {@link org.benchmark.tools.runtime.BenchmarkCaseToolCallbackFactory}.</p>
 */
@Slf4j
@Service
public class BenchmarkToolService {

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final CliSimulator cliSimulator;
    private final BenchmarkScorer benchmarkScorer;
    private final BenchmarkEventLogger eventLogger;

    public BenchmarkToolService(BenchmarkProperties properties,
                                SessionStateManager stateManager,
                                CliSimulator cliSimulator,
                                BenchmarkScorer benchmarkScorer,
                                BenchmarkEventLogger eventLogger) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager must not be null");
        this.cliSimulator = Objects.requireNonNull(cliSimulator, "cliSimulator must not be null");
        this.benchmarkScorer = Objects.requireNonNull(benchmarkScorer, "benchmarkScorer must not be null");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger must not be null");
    }

    public ToolCatalogResponse listAvailableTools(String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        recordDiscovery(sessionId);
        List<String> tools = benchmarkCase.allTools().stream()
                .map(ToolObject::name)
                .toList();
        logEvent("list_available_tools", Map.of(
                "sessionId", sessionId,
                "toolCount", tools.size(),
                "toolNames", tools));
        return new ToolCatalogResponse(tools);
    }

    public ToolDocumentationResponse getToolDocumentation(String sessionId, String toolName) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        recordDiscovery(sessionId);
        stateManager.recordDocumentationRead(sessionId, toolName);
        String documentation = benchmarkCase.documentationForTool(toolName);

        if (documentation == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        logEvent("get_tool_documentation", Map.of(
                "sessionId", sessionId,
                "toolName", toolName,
                "documentationLength", documentation.length(),
                "documentationPreview", preview(documentation)));
        return new ToolDocumentationResponse(toolName, documentation);
    }

    public StateResponse getCurrentState(String sessionId) {
        requireCase(sessionId);
        recordDiscovery(sessionId);
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        logEvent("get_current_state", Map.of(
                "sessionId", sessionId,
                "state", stateManager.getSessionStateSnapshot(sessionId)));
        return new StateResponse(toolStates);
    }

    public CommandExecutionResponse executeBoundTool(String sessionId,
                                                     String toolName,
                                                     String commandName,
                                                     String option) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        String normalizedOption = normalizeOption(option);

        if (stateManager.currentAttemptExecutionCount(sessionId) >= properties.getMaxExecutionsPerAttempt()) {
            ExecutionRecord record = new ExecutionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    false,
                    "STOP: Execution budget exceeded for this attempt. Do not call more tools in this turn. Return a concise summary and wait for retry feedback."
            );
            recordRejectedExecution(sessionId, record);
            return new CommandExecutionResponse(
                    false,
                    record.message(),
                    stateManager.getAllToolStatesSnapshot(sessionId));
        }

        if (stateManager.repeatedFailuresInCurrentAttempt(sessionId, toolName, commandName, normalizedOption)
                >= properties.getMaxRepeatedCommandFailuresPerAttempt()) {
            ExecutionRecord record = new ExecutionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    false,
                    "STOP: This exact command already failed in this attempt. Fix its precondition, read the documentation/state again, or choose a different next step instead of repeating it."
            );
            recordRejectedExecution(sessionId, record);
            return new CommandExecutionResponse(false, record.message(), stateManager.getAllToolStatesSnapshot(sessionId));
        }

        if (scenarioAlreadyComplete(sessionId, benchmarkCase)) {
            ExecutionRecord record = new ExecutionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    false,
                    "REJECTED: Scenario already completed. No further commands are allowed for this session."
            );
            recordRejectedExecution(sessionId, record);
            return new CommandExecutionResponse(false, record.message(), stateManager.getAllToolStatesSnapshot(sessionId));
        }

        ToolObject tool = benchmarkCase.findTool(toolName);
        if (tool == null) {
            ExecutionRecord record = new ExecutionRecord(toolName, commandName, normalizedOption, false,
                    "Unknown tool: " + toolName);
            recordExecution(sessionId, record);
            return new CommandExecutionResponse(false, record.message(), stateManager.getAllToolStatesSnapshot(sessionId));
        }

        CliSimulator.ExecutionResult executionResult = cliSimulator.execute(
                tool, commandName, normalizedOption, stateManager, sessionId);

        String message = executionResult.success() ? executionResult.stdout() : executionResult.stderr();
        ExecutionRecord record = new ExecutionRecord(
                tool.name(), resolvedCommandName(executionResult, commandName),
                normalizedOption, executionResult.success(), message);
        recordExecution(sessionId, record);

        return new CommandExecutionResponse(
                executionResult.success(), message,
                stateManager.getAllToolStatesSnapshot(sessionId));
    }

    private void recordExecution(String sessionId, ExecutionRecord record) {
        stateManager.recordExecution(sessionId, record);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", record.success());
        payload.put("message", record.message());
        payload.put("state", stateManager.getSessionStateSnapshot(sessionId));
        logEvent("execute_tool_command", payload);
    }

    private void recordRejectedExecution(String sessionId, ExecutionRecord record) {
        stateManager.recordExecution(sessionId, record);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("toolName", record.toolName());
        payload.put("commandName", record.commandName());
        payload.put("option", record.option());
        payload.put("success", record.success());
        payload.put("message", record.message());
        payload.put("state", stateManager.getSessionStateSnapshot(sessionId));
        logEvent("reject_tool_command", payload);
    }

    private String resolvedCommandName(CliSimulator.ExecutionResult executionResult, String requestedCommandName) {
        String resolved = executionResult.resolvedCommandName();
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return requestedCommandName == null ? "<unknown>" : requestedCommandName;
    }

    private void logEvent(String eventType, Map<String, Object> data) {
        eventLogger.logEvent(eventType, data);
    }

    private String normalizeOption(String option) {
        return option == null ? "" : option.trim();
    }

    private void recordDiscovery(String sessionId) {
        stateManager.recordDiscovery(sessionId);
    }

    private BenchmarkCaseGenerator.BenchmarkCase requireCase(String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.getBenchmarkCase(sessionId);
        if (benchmarkCase == null) {
            throw new IllegalArgumentException("Unknown benchmark session: " + sessionId);
        }
        return benchmarkCase;
    }

    private boolean scenarioAlreadyComplete(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkScorer.hasSuccessfulScenarioCompletion(
                stateManager.executionLog(sessionId),
                benchmarkCase,
                stateManager.getToolStateSnapshot(sessionId, benchmarkCase.targetToolObject().name())
        );
    }

    private String preview(String text) {
        int limit = 180;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    public record ToolCatalogResponse(List<String> tools) {}
    public record ToolDocumentationResponse(String toolName, String documentation) {}
    public record StateResponse(Map<String, Map<String, String>> toolStates) {}
    public record CommandExecutionResponse(boolean success, String message,
                                           Map<String, Map<String, String>> toolStates) {}
}
