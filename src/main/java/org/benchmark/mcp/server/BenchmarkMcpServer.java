package org.benchmark.mcp.server;

import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.benchmark.utils.RunEventLogger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server implementation: Loopback MCP tool surface used by the benchmark runner.
 */
@Service
public class BenchmarkMcpServer {

    private final SessionStateManager stateManager;
    private final CliSimulator cliSimulator;
    private final RunEventLogger eventLogger;

    public BenchmarkMcpServer(SessionStateManager stateManager,
                              CliSimulator cliSimulator,
                              RunEventLogger eventLogger) {
        this.stateManager = stateManager;
        this.cliSimulator = cliSimulator;
        this.eventLogger = eventLogger;
    }

    @Tool(description = "Lists only the available benchmark tool names for a benchmark session.")
    public ToolCatalogResponse listAvailableTools(
            @ToolParam(description = "Benchmark session identifier provided in the prompt.") String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        stateManager.recordDiscovery(sessionId);
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

    @Tool(description = "Returns the documentation for one tool in the current benchmark session.")
    public ToolDocumentationResponse getToolDocumentation(
            @ToolParam(description = "Benchmark session identifier provided in the prompt.") String sessionId,
            @ToolParam(description = "Exact or case-insensitive tool name.") String toolName) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        stateManager.recordDiscovery(sessionId);
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

    public CommandExecutionResponse executeBoundTool(String sessionId,
                                                     String toolName,
                                                     String commandName,
                                                     String option) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = requireCase(sessionId);
        ToolObject tool = benchmarkCase.findTool(toolName);
        if (tool == null) {
            ExecutionRecord record = new ExecutionRecord(toolName, commandName, option, false,
                    "Unknown tool: " + toolName);
            stateManager.recordExecution(sessionId, record);
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
            ExecutionRecord record = new ExecutionRecord(tool.name(), commandName, option, false,
                    "Unknown command: " + commandName + " for tool " + tool.name());
            stateManager.recordExecution(sessionId, record);
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
        stateManager.recordExecution(sessionId, new ExecutionRecord(
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

    private BenchmarkCaseGenerator.BenchmarkCase requireCase(String sessionId) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.getBenchmarkCase(sessionId);
        if (benchmarkCase == null) {
            throw new IllegalArgumentException("Unknown benchmark session: " + sessionId);
        }
        return benchmarkCase;
    }

    public record ToolCatalogResponse(List<String> tools) {
    }

    public record ToolDocumentationResponse(String toolName, String documentation) {
    }

    public record StateResponse(Map<String, Map<String, String>> toolStates) {
    }

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

    private String preview(String text) {
        int limit = 180;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }
}
