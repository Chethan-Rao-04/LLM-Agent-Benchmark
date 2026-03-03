package org.benchmark.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;

/**
 * Langchain client to connect to Hugging Face Router
 */
public class LlmClient {

    private static final String DEFAULT_BASE_URL = "https://router.huggingface.co/v1/";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-20b:groq";

    private final ChatLanguageModel model;

    public LlmClient(String modelName) {
        this(modelName, null, null);
    }

    public LlmClient(String modelName, String baseUrl, String apiKey) {
        String resolvedModel = firstNonBlank(modelName, System.getenv("LLM_MODEL"), DEFAULT_MODEL);
        String resolvedBaseUrl = firstNonBlank(baseUrl, System.getenv("LLM_BASE_URL"), DEFAULT_BASE_URL);
        String resolvedApiKey = firstNonBlank(apiKey, System.getenv("HF_API_KEY"), System.getenv("OPENAI_API_KEY"));

        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            throw new IllegalStateException("Missing API key. Set HF_API_KEY or OPENAI_API_KEY.");
        }

        this.model = OpenAiChatModel.builder()
                .baseUrl(resolvedBaseUrl)
                .apiKey(resolvedApiKey)
                .modelName(resolvedModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(720))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Sends the prompt and returns the raw response + metadata.
     */
    public LlmResult execute(String systemInstruction, String userQuery) {
        SystemMessage sysMsg = SystemMessage.from(systemInstruction);
        UserMessage userMsg = UserMessage.from(userQuery);

        Response<AiMessage> response = model.generate(sysMsg, userMsg);

        String content = response.content().text();
        int tokenCount = response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0;

        return new LlmResult(content, tokenCount);
    }

    // LLM result record
    public record LlmResult(String content, int tokenUsage) {}

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
