package org.benchmark.config;

import io.micrometer.observation.ObservationRegistry;
import org.benchmark.llm.BenchmarkGuardrailPromptAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.net.http.HttpClient;

/**
 * Spring-managed infrastructure shared across benchmark execution.
 *
 * <p>This configuration owns the boundary to the OpenAI-compatible endpoint so the rest
 * of the codebase can depend on stable Spring AI abstractions instead of transport details.</p>
 */
@Configuration
public class BenchmarkInfrastructureConfig {

    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    /**
     * Creates the shared chat model used by the benchmark runner.
     *
     * <p>We keep this bean explicit instead of relying on full auto-configuration
     * because the benchmark still benefits from one obvious place for the local
     * OpenAI-compatible endpoint and optional Basic Auth wiring.</p>
     *
     * @param properties benchmark configuration properties
     * @param retryTemplate retry policy applied to transient model failures
     * @return configured chat model
     */
    @Bean
    public ChatModel chatModel(BenchmarkProperties properties, RetryTemplate retryTemplate) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (hasBasicAuth(properties)) {
            headers.add(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(properties));
        }

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory(properties));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .apiKey(new NoopApiKey())
                .headers(headers)
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(properties.getLlm().getModel())
                .temperature(properties.getTemperature())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions)
                .retryTemplate(retryTemplate)
                .build();
    }

    /**
     * Builds the retry policy used for transient Spring AI transport failures.
     *
     * @param maxAttempts maximum number of attempts before surfacing the failure
     * @param initialInterval initial retry delay in milliseconds
     * @param multiplier exponential backoff multiplier
     * @param maxInterval upper bound for retry delay in milliseconds
     * @return retry template shared by the chat model
     */
    @Bean
    public RetryTemplate retryTemplate(
            @Value("${spring.ai.retry.max-attempts:5}") int maxAttempts,
            @Value("${spring.ai.retry.backoff.initial-interval:2000}") long initialInterval,
            @Value("${spring.ai.retry.backoff.multiplier:2.0}") double multiplier,
            @Value("${spring.ai.retry.backoff.max-interval:10000}") long maxInterval) {
        return new RetryTemplateBuilder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(initialInterval, multiplier, maxInterval)
                .retryOn(TransientAiException.class)
                .build();
    }

    /**
     * Creates a reusable {@link ChatClient.Builder} so advisor wiring can be composed explicitly.
     *
     * @param chatModel benchmark chat model
     * @return chat client builder
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * Creates the shared chat client with benchmark guardrails attached as a default advisor.
     *
     * @param chatClientBuilder base builder for the configured chat model
     * @param promptAdvisor advisor that appends runtime guardrails to the prompt
     * @return chat client used by the benchmark LLM wrapper
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 BenchmarkGuardrailPromptAdvisor promptAdvisor) {
        return chatClientBuilder.clone()
                .defaultAdvisors(promptAdvisor)
                .build();
    }

    private JdkClientHttpRequestFactory requestFactory(BenchmarkProperties properties) {
        Duration requestTimeout = requestTimeout(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        return requestFactory;
    }

    private Duration requestTimeout(BenchmarkProperties properties) {
        if (properties.getTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("benchmark.timeout-seconds must be greater than zero");
        }
        return Duration.ofSeconds(properties.getTimeoutSeconds());
    }

    /**
     * Returns whether HTTP Basic auth credentials are present.
     *
     * @param properties benchmark configuration properties
     * @return {@code true} when both username and password are set
     */
    private boolean hasBasicAuth(BenchmarkProperties properties) {
        return properties.getLlm().getUsername() != null
                && !properties.getLlm().getUsername().isBlank()
                && properties.getLlm().getPassword() != null
                && !properties.getLlm().getPassword().isBlank();
    }

    /**
     * Builds an RFC7617 Basic Authorization header value.
     *
     * @param properties benchmark configuration properties
     * @return header value (for example {@code Basic abc123...})
     */
    private String basicAuthHeaderValue(BenchmarkProperties properties) {
        String credentials = properties.getLlm().getUsername() + ":" + properties.getLlm().getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

}
