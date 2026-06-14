package org.benchmark.tools.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.benchmark.app.BenchmarkEventLogger;
import org.benchmark.tools.server.BenchmarkToolService;
import org.benchmark.app.BenchmarkScorer;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.CommandObject;
import org.benchmark.model.objects.OptionEntity;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests the execution callback descriptions exposed to the agent.
 */
class BenchmarkCaseToolCallbackFactoryTest {

    @Test
    void descriptionDoesNotLeakCommandsOrOptions() {
        BenchmarkCaseToolCallbackFactory factory = new BenchmarkCaseToolCallbackFactory(createServer());
        ToolObject tool = new ToolObject(
                "MAN-TEST-001",
                "Test tool",
                Domain.MANUFACTURING,
                List.of(new CommandObject(
                        "load_chassis",
                        List.of(new OptionEntity("--dry-run", "Simulate execution")),
                        "Load a chassis",
                        List.of(),
                        Map.of()
                )),
                Map.of("status", "string")
        );

        String description = factory.buildDescription(tool);

        assertFalse(description.contains("load_chassis"));
        assertFalse(description.contains("--dry-run"));
    }

    private BenchmarkToolService createServer() {
        SessionStateManager stateManager = new SessionStateManager();
        return new BenchmarkToolService(
                new BenchmarkProperties(),
                stateManager,
                new CliSimulator(),
                new BenchmarkScorer(stateManager),
                new BenchmarkEventLogger(new ObjectMapper()));
    }
}
