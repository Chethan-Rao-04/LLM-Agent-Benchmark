package org.benchmark.tools.runtime;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.tools.server.BenchmarkToolService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the execution callback descriptions exposed to the agent.
 */
class BenchmarkCaseToolCallbackFactoryTest {

    @Test
    void executionPhaseExposesOnlyStateAndCommandCallbacks() {
        BenchmarkCaseToolCallbackFactory factory = new BenchmarkCaseToolCallbackFactory(createServer());
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator(
                DocumentComplexity.CLEAN,
                7L
        ).generateCases(1, 1, Domain.MANUFACTURING).getFirst();

        ToolCallbackProvider executionCallbacks = factory.createExecution("session-1");

        Set<String> executionNames = callbackNames(executionCallbacks);

        assertTrue(executionNames.contains("getCurrentState"));
        assertTrue(executionNames.contains("executeCommand"));
        assertFalse(executionNames.contains("listAvailableTools"));
        assertFalse(executionNames.contains("getToolDocumentation"));
        assertFalse(executionNames.contains(benchmarkCase.targetToolObject().name()));
    }

    private BenchmarkToolService createServer() {
        try {
            return (BenchmarkToolService) BenchmarkToolService.class.getDeclaredConstructors()[0].newInstance(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Set<String> callbackNames(ToolCallbackProvider provider) {
        return List.of(provider.getToolCallbacks()).stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());
    }
}
