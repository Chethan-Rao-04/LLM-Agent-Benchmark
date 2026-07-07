package org.benchmark.tools.server;

import lombok.RequiredArgsConstructor;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.exec.SessionStateManager.CommandRejectionRecord;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.scoring.BenchmarkScorer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Enforces per-attempt execution limits before simulator state is mutated.
 */
@Component
@RequiredArgsConstructor
class BenchmarkToolExecutionPolicy {

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;
    private final BenchmarkScorer benchmarkScorer;

    Optional<CommandRejectionRecord> rejectIfDisallowed(String sessionId,
                                                        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase,
                                                        String toolName,
                                                        String commandName,
                                                        String normalizedOption) {
        if (stateManager.currentAttemptExecutionCount(sessionId) >= properties.getMaxExecutionsPerAttempt()) {
            return Optional.of(new CommandRejectionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    "STOP: Execution budget exceeded for this attempt. Do not call more tools in this turn. Return a concise summary and wait for retry feedback."
            ));
        }

        if (stateManager.repeatedFailuresInCurrentAttempt(sessionId, toolName, commandName, normalizedOption)
                >= properties.getMaxRepeatedCommandFailuresPerAttempt()) {
            return Optional.of(new CommandRejectionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    "STOP: This exact command already failed in this attempt. Fix its precondition, read the documentation/state again, or choose a different next step instead of repeating it."
            ));
        }

        if (scenarioAlreadyComplete(sessionId, benchmarkCase)) {
            return Optional.of(new CommandRejectionRecord(
                    toolName,
                    commandName,
                    normalizedOption,
                    "REJECTED: Scenario already completed. No further commands are allowed for this session."
            ));
        }

        return Optional.empty();
    }

    private boolean scenarioAlreadyComplete(String sessionId, BenchmarkCaseGenerator.BenchmarkCase benchmarkCase) {
        return benchmarkScorer.hasSuccessfulScenarioCompletion(
                sessionId,
                stateManager.executionLog(sessionId),
                benchmarkCase
        );
    }
}
