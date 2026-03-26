package org.benchmark.config;

import lombok.Data;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

/**
 * Root configuration model loaded from {@code config.yaml}.
 *
 * <p>This class intentionally mirrors the YAML structure so the benchmark can
 * be configured without relying on Spring's property binding.</p>
 */
@Data
public class Config {
    private static final Path CONFIG_PATH = Paths.get("src/main/java/org/benchmark/config.yaml");

    private LlmConfig llm;
    private BenchmarkConfig benchmark;
    private PromptConfig prompt;

    /**
     * Configuration for the remote LLM endpoint used by Spring AI.
     */
    @Data
    public static class LlmConfig {
        /** Model identifier exposed by the remote LLM server. */
        private String model;
        /** Base URL of the OpenAI-compatible LLM server. */
        private String baseUrl;
        /** Optional HTTP basic-auth username for the remote LLM server. */
        private String username;
        /** Optional HTTP basic-auth password for the remote LLM server. */
        private String password;
    }

    /**
     * Benchmark execution settings controlling case generation and retries.
     */
    @Data
    public static class BenchmarkConfig {
        /** Optional domain override; {@code null} means sample across all domains. */
        private Domain domain;
        /** Documentation degradation profile to generate for each case. */
        private String documentComplexity;
        /** Number of distractor tools to include alongside the target tool. */
        private int distractorCount;

        /** Sampling temperature used for model calls. */
        private double temperature;
        /** Timeout applied to benchmark and MCP client operations. */
        private int timeoutSeconds;
        /** Number of benchmark cases to generate per run. */
        private int iterations;
        /** Maximum retries allowed per case. */
        private int maxRetries;

        /** CSV output path for aggregate benchmark results. */
        private String resultsCsv;
        /** JSONL output path for detailed benchmark event traces. */
        private String eventsJsonl;
    }

    /**
     * Prompt templates used during benchmark execution.
     */
    @Data
    public static class PromptConfig {
        /** Base system prompt prepended to every model interaction. */
        private String baseSystemPrompt;
    }

    /**
     * Loads benchmark configuration from the repository-local YAML file.
     *
     * @return parsed configuration object
     */
    public static Config load() {
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            if (in == null) {
                throw new RuntimeException("config.yaml not found at " + CONFIG_PATH);
            }
            Config config = new Yaml().loadAs(in, Config.class);
            if (config == null) {
                throw new RuntimeException("config.yaml is empty.");
            }
            return config;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yaml", e);
        }
    }

    /**
     * Converts the configured complexity string into the corresponding enum value.
     *
     * @return normalized documentation complexity enum
     */
    public DocumentComplexity documentComplexity() {
        return DocumentComplexity.valueOf(benchmark.getDocumentComplexity().trim().toUpperCase());
    }

}
