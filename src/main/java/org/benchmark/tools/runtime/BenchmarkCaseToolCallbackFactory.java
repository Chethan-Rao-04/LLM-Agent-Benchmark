package org.benchmark.tools.runtime;

import org.benchmark.tools.server.BenchmarkToolService;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates the benchmark tool callbacks exposed to the model.
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

    public ToolCallbackProvider createExecution(String sessionId) {
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(buildGetCurrentStateCallback(sessionId));
        callbacks.add(buildExecuteCommandCallback(sessionId));
        return new StaticToolCallbackProvider(callbacks);
    }

    private ToolCallback buildGetCurrentStateCallback(String sessionId) {
        return FunctionToolCallback
                .builder("getCurrentState", (EmptyRequest request) ->
                        benchmarkToolService.getCurrentState(sessionId))
                .description("Returns the current mutable session state for all benchmark tools. "
                        + "Use this to inspect state before or after executing commands.")
                .inputType(EmptyRequest.class)
                .build();
    }

    private ToolCallback buildExecuteCommandCallback(String sessionId) {
        return FunctionToolCallback
                .builder("executeCommand", (BenchmarkToolExecutionRequest request) ->
                        benchmarkToolService.executeCommand(
                                sessionId,
                                request.toolName(),
                                request.commandName(),
                                request.option()
                        ))
                .description("Executes one command on one benchmark tool. "
                        + "Use exact values from the case manual. "
                        + "Arguments are toolName, commandName, and option. "
                        + "When a command has no option, pass option as the empty string \"\".")
                .inputType(BenchmarkToolExecutionRequest.class)
                .build();
    }

    /**
     * Empty request record for callbacks that only need the bound session ID
     * (which is already bound into the callback closure).
     */
    public record EmptyRequest() {
    }

    /**
     * Record to wrap the input for benchmark command execution.
     *
     * @param toolName exact benchmark tool name selected from the case manual
     * @param commandName command name to execute within the selected tool
     * @param option single option flag, or empty string when no option is required
     */
    public record BenchmarkToolExecutionRequest(String toolName, String commandName, String option) {
    }
}
