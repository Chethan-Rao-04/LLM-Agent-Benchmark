package org.benchmark.llm;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.benchmark.config.BenchmarkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientTest {

    @Test
    void shouldThrowTransientExceptionForRetryableModelFailure() {
        TransientAiException cause = new TransientAiException("provider timeout");
        LlmClient client = createClient(throwingModel(cause), noOpAdvisor());

        LlmTransientException exception = assertThrows(LlmTransientException.class,
                () -> client.execute("session-1", 1, "system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldThrowToolCallbackExceptionForUnavailableToolInvocation() {
        IllegalStateException cause = new IllegalStateException("No function callback found");
        LlmClient client = createClient(throwingModel(cause), noOpAdvisor());

        LlmToolCallbackException exception = assertThrows(LlmToolCallbackException.class,
                () -> client.execute("session-1", 1, "system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldThrowServiceExceptionForUnexpectedModelFailure() {
        RuntimeException cause = new RuntimeException("provider down");
        LlmClient client = createClient(throwingModel(cause), noOpAdvisor());

        LlmServiceException exception = assertThrows(LlmServiceException.class,
                () -> client.execute("session-1", 1, "system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldApplyGuardrailsBeforeCallingModel() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel capturingModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.setMaxExecutionsPerAttempt(3);
        properties.getPrompt().setAttemptGuardrails("Use at most {maxExecutionsPerAttempt} tool calls.");

        LlmClient client = createClient(capturingModel, new BenchmarkGuardrailPromptAdvisor(properties),
                ObservationRegistry.create(), properties);
        LlmClient.LlmResult result = client.execute("session-1", 1, "system", "user", new StaticToolCallbackProvider());

        assertEquals("ok", result.content());
        assertTrue(capturedPrompt.get().getSystemMessage().getText().contains("[GUARDRAILS]: Use at most 3 tool calls."));
        assertEquals("user", capturedPrompt.get().getUserMessage().getText());
    }

    @Test
    void shouldRecordObservationTagsForSuccessfulModelCall() {
        RecordingObservationHandler handler = new RecordingObservationHandler();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
        BenchmarkProperties properties = new BenchmarkProperties();
        properties.getLlm().setModel("test-model");

        LlmClient client = createClient(prompt ->
                        new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))),
                noOpAdvisor(),
                registry,
                properties);

        LlmClient.LlmResult result = client.execute("session-42", 3, "system", "user", new StaticToolCallbackProvider());

        assertEquals("ok", result.content());
        assertEquals("benchmark.llm.call", handler.stoppedContext.get().getName());
        assertEquals("test-model",
                handler.stoppedContext.get().getLowCardinalityKeyValue("benchmark.model").getValue());
        assertEquals("success",
                handler.stoppedContext.get().getLowCardinalityKeyValue("benchmark.outcome").getValue());
        assertEquals("session-42",
                handler.stoppedContext.get().getHighCardinalityKeyValue("benchmark.session.id").getValue());
        assertEquals("3",
                handler.stoppedContext.get().getHighCardinalityKeyValue("benchmark.attempt").getValue());
        assertEquals("0",
                handler.stoppedContext.get().getHighCardinalityKeyValue("benchmark.token.usage").getValue());
        assertTrue(Long.parseLong(
                handler.stoppedContext.get().getHighCardinalityKeyValue("benchmark.latency.ms").getValue()) >= 0);
    }

    @Test
    void shouldRequireSpecificToolWhenConfigured() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel capturingModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        var executeCommandCallback = FunctionToolCallback
                .builder("executeCommand", (ExecutionRequest request) -> "ok")
                .description("Execute command")
                .inputType(ExecutionRequest.class)
                .build();

        LlmClient client = createClient(capturingModel, noOpAdvisor());
        client.execute(
                "session-1",
                1,
                List.of(),
                new StaticToolCallbackProvider(executeCommandCallback),
                "executeCommand"
        );

        OpenAiChatOptions options = (OpenAiChatOptions) capturedPrompt.get().getOptions();
        assertNotNull(options.getToolChoice());
        assertTrue(options.getToolChoice().toString().contains("executeCommand"));
    }

    @Test
    void shouldAllowAutomaticToolChoiceWhenNoSpecificToolIsRequired() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel capturingModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };
        var stateCallback = FunctionToolCallback
                .builder("getCurrentState", (ExecutionRequest request) -> "state")
                .description("Inspect state")
                .inputType(ExecutionRequest.class)
                .build();

        LlmClient client = createClient(capturingModel, noOpAdvisor());
        client.execute(
                "session-1",
                1,
                List.of(),
                new StaticToolCallbackProvider(stateCallback)
        );

        OpenAiChatOptions options = (OpenAiChatOptions) capturedPrompt.get().getOptions();
        assertEquals(null, options.getToolChoice());
    }

    @Test
    void shouldSynthesizeAssistantTextWhenModelReturnsOnlyToolCalls() {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "executeCommand",
                "{\"toolName\":\"TARGET-TOOL\",\"commandName\":\"diag_trb\",\"option\":\"\"}"
        );
        ChatModel toolCallingModel = prompt -> new ChatResponse(List.of(
                new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall))
                        .build())
        ));

        LlmClient client = createClient(toolCallingModel, noOpAdvisor());
        LlmClient.LlmResult result = client.execute("session-1", 1, "system", "user", new StaticToolCallbackProvider());

        assertEquals(
                "Calling executeCommand with arguments {\"toolName\":\"TARGET-TOOL\",\"commandName\":\"diag_trb\",\"option\":\"\"}.",
                result.content()
        );
        assertEquals(result.content(), result.assistantMessage().getText());
        assertEquals(1, result.toolCalls().size());
        assertEquals("executeCommand", result.toolCalls().getFirst().name());
    }

    private ChatModel throwingModel(RuntimeException exception) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw exception;
            }
        };
    }

    private BenchmarkGuardrailPromptAdvisor noOpAdvisor() {
        return new BenchmarkGuardrailPromptAdvisor(new BenchmarkProperties());
    }

    private LlmClient createClient(ChatModel chatModel, BenchmarkGuardrailPromptAdvisor promptAdvisor) {
        return createClient(chatModel, promptAdvisor, ObservationRegistry.create(), new BenchmarkProperties());
    }

    private LlmClient createClient(ChatModel chatModel,
                                   BenchmarkGuardrailPromptAdvisor promptAdvisor,
                                   ObservationRegistry observationRegistry,
                                   BenchmarkProperties properties) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(promptAdvisor)
                .build();
        return new LlmClient(chatClient, observationRegistry, properties);
    }

    private static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {
        private final AtomicReference<Observation.Context> stoppedContext = new AtomicReference<>();

        @Override
        public void onStop(Observation.Context context) {
            stoppedContext.set(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    private record ExecutionRequest(String toolName, String commandName, String option) {
    }
}
