package org.benchmark.evaluation;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;

/**
 * Langchain client to connect to Ollama
 */
public class LangChainClient {

    private final ChatLanguageModel model;

    public LangChainClient(String modelName, String baseUrl) {

        this.model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.0) // 0.0 - reproducible
                .timeout(Duration.ofSeconds(360))
                .build();
    }

    /**
     * Sends the prompt and returns the raw response + metadata.
     */
    public LlmResult LlmExecute(String systemInstruction, String userQuery) {


        SystemMessage sysMsg = SystemMessage.from(systemInstruction);
        UserMessage userMsg = UserMessage.from(userQuery);

        Response<AiMessage> response = model.generate(sysMsg, userMsg);

        String content = response.content().text();
        int tokenCount = response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0;

        return new LlmResult(content, tokenCount);
    }

   // LLM result record
    public record LlmResult(String content, int tokenUsage) {}
}