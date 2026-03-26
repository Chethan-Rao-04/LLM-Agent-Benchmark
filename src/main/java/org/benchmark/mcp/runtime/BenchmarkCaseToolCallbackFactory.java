package org.benchmark.mcp.runtime;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.mcp.server.BenchmarkMcpServer;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates one tool callback per generated benchmark tool for the active case.
 */
@Component
public class BenchmarkCaseToolCallbackFactory {

    private final BenchmarkMcpServer benchmarkMcpServer;

    public BenchmarkCaseToolCallbackFactory(BenchmarkMcpServer benchmarkMcpServer) {
        this.benchmarkMcpServer = benchmarkMcpServer;
    }

    public ToolCallbackProvider create(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolObject tool : benchmarkCase.allTools()) {
            callbacks.add(buildToolCallback(sessionId, tool));
        }
        return new StaticToolCallbackProvider(callbacks);
    }

    private ToolCallback buildToolCallback(String sessionId, ToolObject tool) {
        return FunctionToolCallback
                .builder(tool.name(), (BenchmarkToolExecutionRequest request) ->
                        benchmarkMcpServer.executeBoundTool(
                                sessionId,
                                tool.name(),
                                request.command(),
                                request.option()
                        ))
                .description(buildDescription(tool))
                .inputType(BenchmarkToolExecutionRequest.class)
                .build();
    }

    private String buildDescription(ToolObject tool) {
        StringBuilder description = new StringBuilder();
        description.append("Benchmark tool ").append(tool.name())
                .append(". Use this tool by choosing one command supported by it and one option if needed. ")
                .append("Available commands: ");

        for (int i = 0; i < tool.commands().size(); i++) {
            CommandObject command = tool.commands().get(i);
            description.append(command.name());
            if (command.commandOptions() != null && !command.commandOptions().isEmpty()) {
                description.append(" (options: ");
                for (int j = 0; j < command.commandOptions().size(); j++) {
                    description.append(command.commandOptions().get(j).optionName());
                    if (j < command.commandOptions().size() - 1) {
                        description.append(", ");
                    }
                }
                description.append(")");
            } else {
                description.append(" (no option)");
            }
            if (i < tool.commands().size() - 1) {
                description.append("; ");
            }
        }
        return description.toString();
    }
}
