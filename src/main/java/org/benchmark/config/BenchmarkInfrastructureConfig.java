package org.benchmark.config;

import org.benchmark.utils.RunEventLogger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;

/**
 * Spring-managed infrastructure shared across benchmark execution.
 */
@Configuration
public class BenchmarkInfrastructureConfig {

    /**
     * Loads repository-local benchmark configuration.
     *
     * @return parsed benchmark configuration
     */
    @Bean
    public Config benchmarkConfig() {
        return Config.load();
    }

    /**
     * Creates the shared chat model used by the benchmark runner.
     *
     * @param config loaded benchmark configuration
     * @return configured chat model
     */
    @Bean
    public ChatModel chatModel(Config config) {
        // Add explicit HTTP Basic auth only when username/password are configured.
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (hasBasicAuth(config)) {
            headers.add(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(config));
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getLlm().getBaseUrl())
                .apiKey(new NoopApiKey())
                .headers(headers)
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(config.getLlm().getModel())
                .temperature(config.getBenchmark().getTemperature())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions)
                .build();
    }

    /**
     * Creates structured run event logger (JSONL + optional CSV summary).
     * The bean is configured with {@code destroyMethod = "close"} so writers
     * are flushed and closed on application shutdown.
     *
     * @param config loaded benchmark configuration
     * @return event logger instance
     */
    @Bean(destroyMethod = "close")
    public RunEventLogger runEventLogger(Config config) {
        String eventsFilename = config.getBenchmark().getEventsJsonl();
        if (eventsFilename == null || eventsFilename.isBlank()) {
            eventsFilename = "benchmark_run_events.jsonl";
        }
        return new RunEventLogger(eventsFilename);
    }

    /**
     * Returns whether HTTP Basic auth credentials are present.
     *
     * @param config loaded benchmark configuration
     * @return {@code true} when both username and password are set
     */
    private boolean hasBasicAuth(Config config) {
        return config.getLlm().getUsername() != null
                && !config.getLlm().getUsername().isBlank()
                && config.getLlm().getPassword() != null
                && !config.getLlm().getPassword().isBlank();
    }

    /**
     * Builds an RFC7617 Basic Authorization header value.
     *
     * @param config loaded benchmark configuration
     * @return header value (for example {@code Basic abc123...})
     */
    private String basicAuthHeaderValue(Config config) {
        String credentials = config.getLlm().getUsername() + ":" + config.getLlm().getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
