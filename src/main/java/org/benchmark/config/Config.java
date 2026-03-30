package org.benchmark.config;

import lombok.Data;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

/**
 * Root configuration model loaded from {@code config.yaml} on the classpath.
 *
 * <p>This class intentionally mirrors the YAML structure so the benchmark can
 * be configured without relying on Spring's property binding.</p>
 */
@Data
public class Config {

    private LlmConfig llm;
    private BenchmarkConfig benchmark;
    private PromptConfig prompt;

    /**
     * Configuration for the remote LLM endpoint used by Spring AI.
     */
    @Data
    public static class LlmConfig {
        /** Connection type, for example {@code local} or {@code remote}. */
        private String type;
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
        /** Flag to generate multi-step command sequences instead of single commands. */
        private boolean multiStep;

        /** CSV output path for aggregate benchmark results. */
        private String resultsCsv;
        /** JSONL output path for detailed benchmark event traces. */
        private String eventsJsonl;

        /** Seed for reproducible random generation; {@code null} for unseeded. */
        private Long randomSeed;
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
     * Loads benchmark configuration from {@code config.yaml} on the classpath.
     *
     * @return parsed configuration object
     */
    public static Config load() {
        try (InputStream in = Config.class.getResourceAsStream("/config.yaml")) {
            if (in == null) {
                throw new RuntimeException("config.yaml not found on classpath");
            }
            Config config = new Yaml().loadAs(in, Config.class);
            if (config == null) {
                throw new RuntimeException("config.yaml is empty.");
            }
            return config;
        } catch (RuntimeException e) {
            throw e;
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
