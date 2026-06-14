package org.benchmark.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.StaticToolCallbackProvider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmClientTest {

    @Test
    void shouldThrowTransientExceptionForRetryableModelFailure() {
        TransientAiException cause = new TransientAiException("provider timeout");
        LlmClient client = new LlmClient(throwingModel(cause));

        LlmTransientException exception = assertThrows(LlmTransientException.class,
                () -> client.execute("system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldThrowToolCallbackExceptionForUnavailableToolInvocation() {
        IllegalStateException cause = new IllegalStateException("No function callback found");
        LlmClient client = new LlmClient(throwingModel(cause));

        LlmToolCallbackException exception = assertThrows(LlmToolCallbackException.class,
                () -> client.execute("system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldThrowServiceExceptionForUnexpectedModelFailure() {
        RuntimeException cause = new RuntimeException("provider down");
        LlmClient client = new LlmClient(throwingModel(cause));

        LlmServiceException exception = assertThrows(LlmServiceException.class,
                () -> client.execute("system", "user", new StaticToolCallbackProvider()));

        assertSame(cause, exception.getCause());
    }

    private ChatModel throwingModel(RuntimeException exception) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw exception;
            }
        };
    }
}
