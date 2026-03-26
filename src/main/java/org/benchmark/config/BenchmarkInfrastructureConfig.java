package org.benchmark.config;

import org.benchmark.exec.CliSimulator;
import org.benchmark.exec.SessionStateManager;
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

    @Bean
    public Config benchmarkConfig() {
        return Config.load();
    }

    @Bean
    public ChatModel chatModel(Config config) {
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

    @Bean
    public SessionStateManager sessionStateManager() {
        return new SessionStateManager();
    }

    @Bean
    public CliSimulator cliSimulator() {
        return new CliSimulator();
    }

    @Bean
    public RunEventLogger runEventLogger(Config config) {
        String eventsFilename = config.getBenchmark().getEventsJsonl();
        if (eventsFilename == null || eventsFilename.isBlank()) {
            eventsFilename = "benchmark_run_events.jsonl";
        }
        return new RunEventLogger(eventsFilename, config.getBenchmark().getResultsCsv());
    }

    private boolean hasBasicAuth(Config config) {
        return config.getLlm().getUsername() != null
                && !config.getLlm().getUsername().isBlank()
                && config.getLlm().getPassword() != null
                && !config.getLlm().getPassword().isBlank();
    }

    private String basicAuthHeaderValue(Config config) {
        String credentials = config.getLlm().getUsername() + ":" + config.getLlm().getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
