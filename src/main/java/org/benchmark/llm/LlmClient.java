package org.benchmark.llm;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.benchmark.config.BenchmarkProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Thin wrapper over Spring AI chat execution with tool-callback support.
 *
 * <p>This class isolates prompt construction, token extraction, and transport-specific
 * exception translation so the benchmark executor only deals with benchmark semantics.</p>
 */
@Slf4j
@Component
public class LlmClient {

    private static final String OBSERVATION_NAME = "benchmark.llm.call";

    private final ChatClient chatClient;
    private final ObservationRegistry observationRegistry;
    private final BenchmarkProperties properties;

    /**
     * Creates the benchmark LLM client around the shared Spring AI chat client.
     *
     * @param chatClient configured chat client with advisors and transport settings applied
     */
    public LlmClient(ChatClient chatClient,
                     ObservationRegistry observationRegistry,
                     BenchmarkProperties properties) {
        this.chatClient = chatClient;
        this.observationRegistry = observationRegistry;
        this.properties = properties;
    }

    /**
     * Executes one chat-model turn with tool callbacks enabled.
     *
     * @param systemInstruction    system prompt content
     * @param userQuery            user prompt content
     * @param toolCallbackProvider provider exposing tool callbacks
     * @return assistant text plus total token usage
     */
    public LlmResult execute(String sessionId,
                             int attempt,
                             String systemInstruction,
                             String userQuery,
                             ToolCallbackProvider toolCallbackProvider) {
        return execute(sessionId, attempt, List.of(
                new SystemMessage(Objects.requireNonNullElse(systemInstruction, "")),
                new UserMessage(Objects.requireNonNullElse(userQuery, ""))
        ), toolCallbackProvider);
    }

    /**
     * Executes one chat-model turn with the provided message history and tool surface.
     *
     * @param sessionId active benchmark session identifier
     * @param attempt current benchmark attempt number
     * @param messages complete prompt history for this turn
     * @param toolCallbackProvider provider exposing the callbacks allowed in this phase
     * @return assistant message plus token usage for this model response
     */
    public LlmResult execute(String sessionId,
                             int attempt,
                             List<Message> messages,
                             ToolCallbackProvider toolCallbackProvider) {
        return execute(sessionId, attempt, messages, toolCallbackProvider, null);
    }

    /**
     * Executes one chat-model turn and optionally requires a specific tool callback.
     *
     * @param sessionId active benchmark session identifier
     * @param attempt current benchmark attempt number
     * @param messages complete prompt history for this turn
     * @param toolCallbackProvider provider exposing the callbacks allowed in this phase
     * @param requiredToolName tool callback the provider must request, or {@code null} for automatic choice
     * @return assistant message plus token usage for this model response
     */
    public LlmResult execute(String sessionId,
                             int attempt,
                             List<Message> messages,
                             ToolCallbackProvider toolCallbackProvider,
                             String requiredToolName) {
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolCallbackProvider.getToolCallbacks());

        if (requiredToolName != null && !requiredToolName.isBlank()) {
            optionsBuilder.toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.function(requiredToolName));
        }

        OpenAiChatOptions options = optionsBuilder.build();

        Observation observation = Observation.start(OBSERVATION_NAME, observationRegistry)
                .contextualName("benchmark llm call");
        tagHighCardinality(observation, "benchmark.session.id", blankSafe(sessionId, "<unknown-session>"));
        tagHighCardinality(observation, "benchmark.attempt", Integer.toString(attempt));
        tagLowCardinality(observation, "benchmark.model", configuredModel());

        long startTime = System.nanoTime();
        try {
            try (Observation.Scope ignored = observation.openScope()) {
                ChatResponse response = chatClient.prompt(buildPrompt(messages, options))
                        .call()
                        .chatResponse();
                AssistantMessage assistantMessage = enrichAssistantMessage(extractAssistantMessage(response));
                String content = assistantMessage.getText();
                int tokenCount = extractTokenUsage(response).orElseGet(() -> {
                    log.warn("Token usage metadata is absent from the model response. Token counts will be inaccurate.");
                    return 0;
                });
                tagLowCardinality(observation, "benchmark.outcome", "success");
                tagHighCardinality(observation, "benchmark.token.usage", Integer.toString(tokenCount));
                return new LlmResult(assistantMessage, content, assistantMessage.getToolCalls(), tokenCount);
            }
        } catch (TransientAiException e) {
            observation.error(e);
            tagLowCardinality(observation, "benchmark.outcome", "transient_failure");
            log.warn("Transient model failure: {}", summarize(e.getMessage()));
            throw new LlmTransientException("Transient model failure", e);
        } catch (IllegalStateException e) {
            observation.error(e);
            tagLowCardinality(observation, "benchmark.outcome", "tool_callback_failure");
            log.warn("Model attempted to call an unavailable tool: {}", summarize(e.getMessage()));
            throw new LlmToolCallbackException("Model attempted to call an unavailable tool", e);
        } catch (RuntimeException e) {
            observation.error(e);
            tagLowCardinality(observation, "benchmark.outcome", "service_failure");
            log.warn("Model call failed: {}", summarize(e.getMessage()));
            throw new LlmServiceException("Model call failed", e);
        } finally {
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            tagHighCardinality(observation, "benchmark.latency.ms", Long.toString(latencyMs));
            if (observation.getContext().getLowCardinalityKeyValue("benchmark.outcome") == null) {
                tagLowCardinality(observation, "benchmark.outcome", "unknown");
            }
            observation.stop();
        }
    }

    private Prompt buildPrompt(List<Message> messages, OpenAiChatOptions options) {
        return new Prompt(List.copyOf(messages), options);
    }

    private AssistantMessage extractAssistantMessage(ChatResponse response) {
        return Optional.ofNullable(response)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput())
                .orElseGet(() -> AssistantMessage.builder().content("").build());
    }

    private AssistantMessage enrichAssistantMessage(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return AssistantMessage.builder().content("<no assistant message>").build();
        }
        String content = assistantMessage.getText();
        if (content != null && !content.isBlank()) {
            return assistantMessage;
        }
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return AssistantMessage.builder().content("<no assistant message>").build();
        }
        return AssistantMessage.builder()
                .content(summarizeToolCalls(toolCalls))
                .toolCalls(toolCalls)
                .build();
    }

    private String summarizeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(this::summarizeToolCall)
                .collect(Collectors.joining(" Then "));
    }

    private String summarizeToolCall(AssistantMessage.ToolCall toolCall) {
        String name = blankSafe(toolCall.name(), "unknownTool");
        String arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank() || "{}".equals(arguments.trim())) {
            return "Calling " + name + ".";
        }
        return "Calling " + name + " with arguments " + arguments.trim() + ".";
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
     * @param toolCalls  tool calls requested by the assistant during this single model turn
     * @param tokenUsage total tokens reported by the model provider
     */
    public record LlmResult(AssistantMessage assistantMessage,
                            String content,
                            List<AssistantMessage.ToolCall> toolCalls,
                            int tokenUsage) {
    }

    private String configuredModel() {
        if (properties == null || properties.getLlm() == null) {
            return "<unknown-model>";
        }
        return blankSafe(properties.getLlm().getModel(), "<unknown-model>");
    }

    private void tagLowCardinality(Observation observation, String key, String value) {
        observation.getContext().removeLowCardinalityKeyValue(key);
        observation.lowCardinalityKeyValue(key, value);
    }

    private void tagHighCardinality(Observation observation, String key, String value) {
        observation.getContext().removeHighCardinalityKeyValue(key);
        observation.highCardinalityKeyValue(key, value);
    }

    private String summarize(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 220 ? singleLine : singleLine.substring(0, 220) + "...";
    }

    private String blankSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
