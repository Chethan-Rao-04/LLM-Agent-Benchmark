package org.benchmark.mcp.server;

import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.mcp.runtime.BenchmarkExecutionRecord;
import org.benchmark.mcp.runtime.BenchmarkSessionRegistry;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.utils.RunEventLogger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server implementation: Loopback MCP tool surface used by the benchmark runner.
 *
 * <p>The LLM uses these tools to discover degraded documentation, inspect
 * current state, and execute synthetic CLI commands.</p>
 */
@Service
public class BenchmarkMcpServer {

    private final BenchmarkSessionRegistry sessionRegistry;
    private final SessionStateManager stateManager;
    private final CliSimulator cliSimulator;
    private final RunEventLogger eventLogger;

    /**
     * Creates the MCP server used by the loopback benchmark client.
     *
     * @param sessionRegistry benchmark session registry
     * @param stateManager mutable state manager shared across sessions
     * @param cliSimulator simulator that executes synthetic commands
     * @param eventLogger structured event logger for traceability
     */
    public BenchmarkMcpServer(BenchmarkSessionRegistry sessionRegistry,
                              SessionStateManager stateManager,
                              CliSimulator cliSimulator,
                              RunEventLogger eventLogger) {
        this.sessionRegistry = sessionRegistry;
        this.stateManager = stateManager;
        this.cliSimulator = cliSimulator;
        this.eventLogger = eventLogger;
    }

    /**
     * Lists all tools visible in the current benchmark session.
     *
     * @param sessionId benchmark session identifier
     * @return catalog of target and distractor tools
     */
    @Tool(description = "Lists only the available benchmark tool names for a benchmark session.")
    public ToolCatalogResponse listAvailableTools(
            @ToolParam(description = "Benchmark session identifier provided in the prompt.") String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        List<String> tools = benchmarkCase.allTools().stream()
                .map(ToolObject::name)
                .toList();
        eventLogger.log("mcp_list_available_tools", Map.of(
                "sessionId", sessionId,
                "toolCount", tools.size(),
                "toolNames", tools
        ));
        return new ToolCatalogResponse(tools);
    }

    /**
     * Returns documentation for one tool in the current benchmark case.
     *
     * @param sessionId benchmark session identifier
     * @param toolName tool name to retrieve documentation for
     * @return tool documentation payload
     */
    @Tool(description = "Returns the documentation for one tool in the current benchmark session.")
    public ToolDocumentationResponse getToolDocumentation(
            @ToolParam(description = "Benchmark session identifier provided in the prompt.") String sessionId,
            @ToolParam(description = "Exact or case-insensitive tool name.") String toolName) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        String documentation = benchmarkCase.documentationForTool(toolName);

        if (documentation == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        eventLogger.log("mcp_get_tool_documentation", Map.of(
                "sessionId", sessionId,
                "toolName", toolName,
                "documentationLength", documentation.length(),
                "documentationPreview", preview(documentation)
        ));
        return new ToolDocumentationResponse(toolName, documentation);
    }

    /**
     * Returns the current mutable session state.
     *
     * @param sessionId benchmark session identifier
     * @return current state payload
     */
    @Tool(description = "Returns the current mutable session state map for the benchmark case.")
    public StateResponse getCurrentState(
            @ToolParam(description = "Benchmark session identifier provided in the prompt.") String sessionId) {
        requireCase(sessionId);
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        eventLogger.log("mcp_get_current_state", Map.of(
                "sessionId", sessionId,
                "state", stateManager.getSessionStateSnapshot(sessionId)
        ));
        return new StateResponse(toolStates);
    }

    /**
     * Executes one command against one generated benchmark tool.
     *
     * <p>This is intentionally not exposed as a generic MCP tool. Instead, the
     * runner creates one tool callback per generated benchmark tool and binds
     * it to this executor.</p>
     */
    public CommandExecutionResponse executeBoundTool(String sessionId,
                                                     String toolName,
                                                     String commandName,
                                                     String option) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        ToolObject tool = benchmarkCase.findTool(toolName);
        if (tool == null) {
            BenchmarkExecutionRecord record = new BenchmarkExecutionRecord(toolName, commandName, option, false,
                    "Unknown tool: " + toolName);
            sessionRegistry.record(sessionId, record);
            logExecutionEvent(sessionId, toolName, commandName, option, false, record.message());
            return new CommandExecutionResponse(
                    false,
                    record.message(),
                    stateManager.getAllToolStatesSnapshot(sessionId)
            );
        }

        CommandObject command = tool.commands().stream()
                .filter(cmd -> cmd.name().equalsIgnoreCase(commandName))
                .findFirst()
                .orElse(null);
        if (command == null) {
            BenchmarkExecutionRecord record = new BenchmarkExecutionRecord(tool.name(), commandName, option, false,
                    "Unknown command: " + commandName + " for tool " + tool.name());
            sessionRegistry.record(sessionId, record);
            logExecutionEvent(sessionId, tool.name(), commandName, option, false, record.message());
            return new CommandExecutionResponse(
                    false,
                    record.message(),
                    stateManager.getAllToolStatesSnapshot(sessionId)
            );
        }

        String normalizedOption = option == null ? "" : option;
        CliSimulator.ExecutionResult executionResult =
                cliSimulator.execute(command, normalizedOption, stateManager, sessionId, tool.name());
        String message = executionResult.success() ? executionResult.stdout() : executionResult.stderr();
        sessionRegistry.record(sessionId, new BenchmarkExecutionRecord(
                tool.name(),
                command.name(),
                normalizedOption,
                executionResult.success(),
                message
        ));
        logExecutionEvent(sessionId, tool.name(), command.name(), normalizedOption,
                executionResult.success(), message);

        return new CommandExecutionResponse(
                executionResult.success(),
                message,
                stateManager.getAllToolStatesSnapshot(sessionId)
        );
    }

    /**
     * Resolves the benchmark case associated with a session ID.
     *
     * @param sessionId session identifier
     * @return benchmark case bound to the session
     */
    private BenchmarkCaseGenerator.BenchmarkCase requireCase(String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = sessionRegistry.getBenchmarkCase(sessionId);
        if (benchmarkCase == null) {
            throw new IllegalArgumentException("Unknown benchmark session: " + sessionId);
        }
        return benchmarkCase;
    }

    /** Response wrapper containing all visible tool summaries for a session. */
    public record ToolCatalogResponse(List<String> tools) {
    }

    /** Response wrapper for a single tool's documentation text. */
    public record ToolDocumentationResponse(String toolName, String documentation) {
    }

    /** Response wrapper exposing current mutable session state. */
    public record StateResponse(Map<String, Map<String, String>> toolStates) {
    }

    /** Response wrapper for simulator command execution results. */
    public record CommandExecutionResponse(boolean success,
                                           String message,
                                           Map<String, Map<String, String>> toolStates) {
    }

    private void logExecutionEvent(String sessionId,
                                   String toolName,
                                   String commandName,
                                   String option,
                                   boolean success,
                                   String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("toolName", toolName);
        payload.put("commandName", commandName);
        payload.put("option", option);
        payload.put("success", success);
        payload.put("message", message);
        payload.put("state", stateManager.getSessionStateSnapshot(sessionId));
        eventLogger.log("mcp_execute_tool_command", payload);
    }

    /**
     * Truncates documentation text for log-friendly previews.
     *
     * @param text source text
     * @return truncated preview string
     */
    private String preview(String text) {
        int limit = 180;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }
}
