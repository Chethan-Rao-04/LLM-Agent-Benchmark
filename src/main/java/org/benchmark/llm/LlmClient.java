package org.benchmark.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thin wrapper over Spring AI chat execution with tool-callback support.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LlmClient {

    private final ChatModel chatModel;

    /**
     * Executes one chat-model turn with MCP tool callbacks enabled.
     *
     * @param systemInstruction    system prompt content
     * @param userQuery            user prompt content
     * @param toolCallbackProvider provider exposing tool callbacks
     * @return assistant text plus total token usage
     */
    public LlmResult execute(String systemInstruction, String userQuery, ToolCallbackProvider toolCallbackProvider) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(true)
                .toolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();

        try {
            ChatResponse response = chatModel.call(buildPrompt(systemInstruction, userQuery, options));
            String content = extractContent(response);
            int tokenCount = extractTokenUsage(response).orElseGet(() -> {
                log.warn("Token usage metadata is absent from the model response. Token counts will be inaccurate.");
                return 0;
            });
            return new LlmResult(content, tokenCount);
        } catch (TransientAiException e) {
            log.warn("Transient model failure: {}", summarize(e.getMessage()));
            throw new LlmTransientException("Transient model failure", e);
        } catch (IllegalStateException e) {
            log.warn("Model attempted to call an unavailable tool: {}", summarize(e.getMessage()));
            throw new LlmToolCallbackException("Model attempted to call an unavailable tool", e);
        } catch (RuntimeException e) {
            log.warn("Model call failed: {}", summarize(e.getMessage()));
            throw new LlmServiceException("Model call failed", e);
        }
    }

    private Prompt buildPrompt(String systemInstruction, String userQuery, OpenAiChatOptions options) {
        return new Prompt(
                List.of(
                        new SystemMessage(Objects.requireNonNullElse(systemInstruction, "")),
                        new UserMessage(Objects.requireNonNullElse(userQuery, ""))
                ),
                options
        );
    }

    private String extractContent(ChatResponse response) {
        return Optional.ofNullable(response)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput())
                .map(output -> output.getText())
                .orElse("");
    }

    private Optional<Integer> extractTokenUsage(ChatResponse response) {
        return Optional.ofNullable(response)
                .map(ChatResponse::getMetadata)
                .map(metadata -> metadata.getUsage())
                .map(usage -> usage.getTotalTokens());
    }

    /**
     * Minimal result wrapper for one LLM call.
     *
     * @param content    assistant text content
     * @param tokenUsage total tokens reported by the model provider
     */
    public record LlmResult(String content, int tokenUsage) {
    }

    private String summarize(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 220 ? singleLine : singleLine.substring(0, 220) + "...";
    }
}
