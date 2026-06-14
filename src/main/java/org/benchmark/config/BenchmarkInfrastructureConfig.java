package org.benchmark.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.net.http.HttpClient;

/**
 * Spring-managed infrastructure shared across benchmark execution.
 */
@Configuration
public class BenchmarkInfrastructureConfig {

    /**
     * Creates the shared chat model used by the benchmark runner.
     *
     * <p>We keep this bean explicit instead of relying on full auto-configuration
     * because the benchmark still benefits from one obvious place for the local
     * OpenAI-compatible endpoint and optional Basic Auth wiring.</p>
     *
     * @param properties benchmark configuration properties
     * @return configured chat model
     */
    @Bean
    public ChatModel chatModel(BenchmarkProperties properties) {
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
