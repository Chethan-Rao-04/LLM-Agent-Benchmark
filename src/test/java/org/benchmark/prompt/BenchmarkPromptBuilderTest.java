package org.benchmark.prompt;

import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.benchmark.model.objects.ToolObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkPromptBuilderTest {

    @Test
    void buildsInitialMessagesWithManualAndCurrentState() {
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.getPrompt().setBaseSystemPrompt("Base policy.");
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 7L)
                .generateCases(1, 1, Domain.MANUFACTURING)
                .getFirst();
        ToolObject tool = new ToolObject(
                "TARGET-TOOL",
                "Target tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("status", "string")
        );
        stateManager.initializeSession("session-1", benchmarkCase, List.of(tool));
        stateManager.updateToolState("session-1", "TARGET-TOOL", "status", "waiting");

        BenchmarkPromptBuilder builder = new BenchmarkPromptBuilder(properties, stateManager);

        List<Message> messages = builder.buildInitialMessages("session-1", "Verify the target");

        assertEquals(2, messages.size());
        assertTrue(messages.getFirst().getText().contains("Case Manual:"));
        assertTrue(messages.getFirst().getText().contains(benchmarkCase.caseManual()));
        assertTrue(messages.getFirst().getText().contains("When you call a tool, include a short assistant message"));
        assertTrue(messages.getFirst().getText().contains("Do not leave the assistant message empty."));
        assertTrue(messages.getFirst().getText().contains("each attempt must call executeCommand exactly once"));
        assertTrue(messages.get(1).getText().contains("Verify the target"));
        assertTrue(messages.get(1).getText().contains("TARGET-TOOL"));
        assertTrue(messages.get(1).getText().contains("waiting"));
    }

    @Test
    void buildsRetryMessageWithoutReinjectingManual() {
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.getPrompt().setBaseSystemPrompt("Base policy.");
        SessionStateManager stateManager = new SessionStateManager();
        BenchmarkCaseGenerator.BenchmarkCase benchmarkCase = new BenchmarkCaseGenerator(DocumentComplexity.CLEAN, 7L)
                .generateCases(1, 1, Domain.MANUFACTURING)
                .getFirst();
        ToolObject tool = new ToolObject(
                "TARGET-TOOL",
                "Target tool",
                Domain.MANUFACTURING,
                List.of(),
                Map.of("status", "string")
        );
        stateManager.initializeSession("session-1", benchmarkCase, List.of(tool));
        stateManager.updateToolState("session-1", "TARGET-TOOL", "status", "configured");

        BenchmarkPromptBuilder builder = new BenchmarkPromptBuilder(properties, stateManager);

        UserMessage retryMessage = builder.buildRetryMessage(
                "session-1",
                1,
                "SUCCESS: The latest attempt produced a completed runtime state."
        );

        assertTrue(retryMessage.getText().contains("Attempt 1 retry context"));
        assertTrue(retryMessage.getText().contains("SUCCESS: The latest attempt produced a completed runtime state."));
        assertTrue(retryMessage.getText().contains("configured"));
        assertTrue(retryMessage.getText().contains("TARGET-TOOL"));
        assertTrue(!retryMessage.getText().contains("Case Manual:"));
    }
}
