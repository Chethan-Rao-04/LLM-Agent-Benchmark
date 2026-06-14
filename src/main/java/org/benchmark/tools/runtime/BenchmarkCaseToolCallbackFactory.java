package org.benchmark.tools.runtime;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.tools.server.BenchmarkToolService;
import org.benchmark.model.objects.ToolObject;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates tool callbacks for both discovery and execution in each benchmark case.
 *
 * <p>Discovery tools ({@code listAvailableTools}, {@code getToolDocumentation},
 * {@code getCurrentState}) are registered as direct {@link FunctionToolCallback}
 * instances so the LLM can call them without any indirection.</p>
 */
@Component
public class BenchmarkCaseToolCallbackFactory {

    private final BenchmarkToolService benchmarkToolService;

    /**
     * Creates the callback factory used to expose generated benchmark tools.
     *
     * @param benchmarkToolService service that executes bound tool requests
     */
    public BenchmarkCaseToolCallbackFactory(BenchmarkToolService benchmarkToolService) {
        this.benchmarkToolService = Objects.requireNonNull(benchmarkToolService, "benchmarkToolService must not be null");
    }

    /**
     * Creates all tool callbacks for one benchmark case: 3 discovery tools plus
     * one execution callback per generated tool.
     *
     * @param sessionId     active benchmark session id
     * @param benchmarkCase case containing target and distractor tools
     * @return provider exposing discovery and execution tool callbacks
     */
    public ToolCallbackProvider create(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<ToolCallback> callbacks = new ArrayList<>();

        // Discovery tools — registered as direct function callbacks so the LLM
        // sees them by their exact name without any server prefix.
        callbacks.add(buildListAvailableToolsCallback(sessionId));
        callbacks.add(buildGetToolDocumentationCallback(sessionId));
        callbacks.add(buildGetCurrentStateCallback(sessionId));

        // Execution tools — one per generated benchmark tool.
        for (ToolObject tool : benchmarkCase.allTools()) {
            callbacks.add(buildToolCallback(sessionId, tool));
        }
        return new StaticToolCallbackProvider(callbacks);
    }

    // ── Discovery tool callbacks ─────────────────────────────────────────

    private ToolCallback buildListAvailableToolsCallback(String sessionId) {
        return FunctionToolCallback
                .builder("listAvailableTools", (DiscoveryRequest request) ->
                        benchmarkToolService.listAvailableTools(sessionId))
                .description("Lists all available benchmark tool names for the current session. "
                        + "Call this first to discover which tools exist before reading their documentation.")
                .inputType(DiscoveryRequest.class)
                .build();
    }

    private ToolCallback buildGetToolDocumentationCallback(String sessionId) {
        return FunctionToolCallback
                .builder("getToolDocumentation", (DocumentationRequest request) ->
                        benchmarkToolService.getToolDocumentation(sessionId, request.toolName()))
                .description("Returns the full documentation (commands, options, effects) for one benchmark tool. "
                        + "Call this after listAvailableTools to learn the exact command names and options.")
                .inputType(DocumentationRequest.class)
                .build();
    }

    private ToolCallback buildGetCurrentStateCallback(String sessionId) {
        return FunctionToolCallback
                .builder("getCurrentState", (DiscoveryRequest request) ->
                        benchmarkToolService.getCurrentState(sessionId))
                .description("Returns the current mutable session state for all benchmark tools. "
                        + "Use this to inspect state before or after executing commands.")
                .inputType(DiscoveryRequest.class)
                .build();
    }

    // ── Execution tool callbacks ─────────────────────────────────────────

    /**
     * Creates one function callback bound to one generated benchmark tool.
     *
     * @param sessionId active benchmark session id
     * @param tool      tool whose commands are executed by this callback
     * @return function-style tool callback
     */
    private ToolCallback buildToolCallback(String sessionId, ToolObject tool) {
        return FunctionToolCallback
                .builder(tool.name(), (BenchmarkToolExecutionRequest request) ->
                        benchmarkToolService.executeBoundTool(
                                sessionId,
                                tool.name(),
                                request.command(),
                                request.option()
                        ))
                .description(buildDescription(tool))
                .inputType(BenchmarkToolExecutionRequest.class)
                .build();
    }

    /**
     * Builds a generic execution-only description for one benchmark tool.
     *
     * <p>The description intentionally avoids exposing command names or options so
     * agents must use discovery tools to learn the tool surface before execution.</p>
     *
     * @param tool generated benchmark tool
     * @return callback description shown to the model
     */
    String buildDescription(ToolObject tool) {
        return "Benchmark execution tool " + tool.name()
                + ". Use this callback only when you already know the exact command name"
                + " and optional flag for this tool."
                + " Call listAvailableTools and getToolDocumentation first to discover"
                + " available commands and options.";
    }

    // ── Request records ──────────────────────────────────────────────────

    /**
     * Empty request record for discovery tools that only need the session ID
     * (which is already bound into the callback closure).
     */
    public record DiscoveryRequest() {
    }

    /**
     * Request record for getToolDocumentation, which needs a tool name.
     *
     * @param toolName exact or case-insensitive tool name to look up
     */
    public record DocumentationRequest(String toolName) {
    }

    /**
     * Record to wrap the input for one generated benchmark tool callback.
     *
     * @param command command name to execute within the selected tool
     * @param option  single option flag, or empty string when no option is required
     */
    public record BenchmarkToolExecutionRequest(String command, String option) {
    }
}
