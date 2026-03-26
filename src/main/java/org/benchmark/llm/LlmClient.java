package org.benchmark.llm;

import org.springframework.ai.chat.messages.AssistantMessage;
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
@Component
public class LlmClient {

    private final ChatModel chatModel;

    public LlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Executes one chat-model turn with MCP tool callbacks enabled.
     *
     * @param systemInstruction system prompt content
     * @param userQuery user prompt content
     * @param toolCallbackProvider provider exposing MCP-backed tool callbacks
     * @return assistant text plus total token usage
     */
    public LlmResult execute(String systemInstruction, String userQuery, ToolCallbackProvider toolCallbackProvider) {
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

        ChatResponse response = chatModel.call(prompt);
        AssistantMessage assistantMessage = response.getResult() == null ? null : response.getResult().getOutput();
        String content = assistantMessage == null || assistantMessage.getText() == null
                ? ""
                : assistantMessage.getText();

        int tokenCount = response.getMetadata() != null
                && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getTotalTokens() != null
                ? response.getMetadata().getUsage().getTotalTokens()
                : 0;

        return new LlmResult(content, tokenCount);
    }

    /**
     * Minimal result wrapper for one LLM call.
     *
     * @param content assistant text content
     * @param tokenUsage total tokens reported by the model provider
     */
    public record LlmResult(String content, int tokenUsage) {
    }
}
