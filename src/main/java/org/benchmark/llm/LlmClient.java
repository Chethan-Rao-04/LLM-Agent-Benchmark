package org.benchmark.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin wrapper over Spring AI chat execution with MCP tool-callback support.
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
     * @param toolCallbackProvider provider exposing MCP-backed tool callbacks
     * @return assistant text plus total token usage
     */
    public LlmResult execute(String systemInstruction, String userQuery, ToolCallbackProvider toolCallbackProvider) {
        // Each call enables internal tool execution and exposes MCP callbacks.
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(true)
                .toolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();

        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(systemInstruction),
                        new UserMessage(userQuery)
                ),
                options
        );

        // Execute one turn and extract assistant text + usage metadata.
        ChatResponse response = chatModel.call(prompt);
        String content = "";
        if (response.getResult() != null && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null) {
            content = response.getResult().getOutput().getText();
        }

        int tokenCount = 0;
        if (response.getMetadata() != null
                && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getTotalTokens() != null) {
            tokenCount = response.getMetadata().getUsage().getTotalTokens();
        } else {
            log.warn("Token usage metadata is absent from the model response — token counts will be inaccurate");
        }

        return new LlmResult(content, tokenCount);
    }

    /**
     * Minimal result wrapper for one LLM call.
     *
     * @param content    assistant text content
     * @param tokenUsage total tokens reported by the model provider
     */
    public record LlmResult(String content, int tokenUsage) {
    }
}
