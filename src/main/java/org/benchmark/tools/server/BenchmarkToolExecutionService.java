package org.benchmark.tools.server;

import lombok.RequiredArgsConstructor;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.CommandOptionNormalizer;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.CommandRejectionRecord;
import org.benchmark.exec.SessionStateManager.ExecutionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.ToolObject;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes benchmark tool commands after policy checks and records the resulting runtime evidence.
 */
@Service
@RequiredArgsConstructor
class BenchmarkToolExecutionService {

    private final SessionStateManager stateManager;
    private final CliSimulator cliSimulator;
    private final BenchmarkToolExecutionPolicy executionPolicy;
    private final BenchmarkToolEventPublisher eventPublisher;

    BenchmarkToolService.CommandExecutionResponse executeCommand(String sessionId,
                                                                 String toolName,
                                                                 String commandName,
                                                                 String option) {
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = stateManager.requireBenchmarkCase(sessionId);
        return stateManager.withSessionLock(sessionId, () -> {
            String normalizedOption = CommandOptionNormalizer.normalize(option);
            if (stateManager.attemptExecutionConsumed(sessionId)) {
                return rejectExecution(sessionId, new CommandRejectionRecord(
                        toolName,
                        commandName,
                        normalizedOption,
                        "STOP: This attempt already consumed its single execution. Return a concise summary and wait for retry feedback."
                ));
            }

            ToolObject tool = benchmarkCase.findTool(toolName);
            if (tool == null) {
                CommandRejectionRecord record = new CommandRejectionRecord(toolName, commandName, normalizedOption,
                        "Unknown tool: " + toolName);
                return rejectExecution(sessionId, record);
            }

            if (!isKnownCommand(tool, commandName)) {
                CommandRejectionRecord record = new CommandRejectionRecord(toolName, commandName, normalizedOption,
                        unknownCommandMessage(tool, commandName));
                return rejectExecution(sessionId, record);
            }

            var rejection = executionPolicy.rejectIfDisallowed(
                    sessionId, benchmarkCase, toolName, commandName, normalizedOption);
            if (rejection.isPresent()) {
                return rejectExecution(sessionId, rejection.get());
            }

            if (!isTargetTool(benchmarkCase, tool)) {
                stateManager.markAttemptExecutionConsumed(sessionId);
                return respondExecution(sessionId, new ExecutionRecord(
                        tool.name(),
                        commandName,
                        normalizedOption,
                        false,
                        "Wrong tool selected: " + tool.name()
                                + ". Expected target tool: " + expectedTargetTools(benchmarkCase)
                ));
            }

            CliSimulator.ExecutionResult executionResult = cliSimulator.execute(
                    tool, commandName, normalizedOption, stateManager, sessionId);
            stateManager.markAttemptExecutionConsumed(sessionId);

            String message = executionResult.success() ? executionResult.stdout() : executionResult.stderr();
            ExecutionRecord record = new ExecutionRecord(
                    tool.name(), resolvedCommandName(executionResult, commandName),
                    normalizedOption, executionResult.success(), message);
            return respondExecution(sessionId, record);
        });
    }

    private BenchmarkToolService.CommandExecutionResponse rejectExecution(String sessionId,
                                                                         CommandRejectionRecord record) {
        stateManager.recordCommandRejection(sessionId, record);
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        eventPublisher.publishCommandRejection(sessionId, record, "reject_tool_command");
        return new BenchmarkToolService.CommandExecutionResponse(
                BenchmarkToolService.CommandOutcomeType.REJECTED,
                false,
                record.message(),
                toolStates
        );
    }

    private BenchmarkToolService.CommandExecutionResponse respondExecution(String sessionId, ExecutionRecord record) {
        stateManager.recordExecution(sessionId, record);
        Map<String, Map<String, String>> toolStates = stateManager.getAllToolStatesSnapshot(sessionId);
        eventPublisher.publishExecution(sessionId, record, "execute_tool_command");
        return new BenchmarkToolService.CommandExecutionResponse(
                BenchmarkToolService.CommandOutcomeType.EXECUTED,
                record.success(),
                record.message(),
                toolStates
        );
    }

    private String resolvedCommandName(CliSimulator.ExecutionResult executionResult, String requestedCommandName) {
        String resolved = executionResult.resolvedCommandName();
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return requestedCommandName == null ? "<unknown>" : requestedCommandName;
    }

    private boolean isKnownCommand(ToolObject tool, String commandName) {
        if (commandName == null || commandName.isBlank() || tool.commands() == null) {
            return false;
        }
        return tool.commands().stream()
                .map(CommandObject::name)
                .anyMatch(name -> name.equalsIgnoreCase(commandName));
    }

    private boolean isTargetTool(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase, ToolObject tool) {
        return benchmarkCase.targetTools().stream()
                .anyMatch(targetTool -> targetTool.name().equalsIgnoreCase(tool.name()));
    }

    private String expectedTargetTools(BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkCase.targetTools().stream()
                .map(ToolObject::name)
                .collect(Collectors.joining(", "));
    }

    private String unknownCommandMessage(ToolObject tool, String commandName) {
        String availableCommands = tool.commands().stream()
                .map(CommandObject::name)
                .collect(Collectors.joining(", "));
        return "REJECTED: Unknown command " + commandName + " for tool "
                + tool.name() + ". Use one of these commands: " + availableCommands;
    }

}
