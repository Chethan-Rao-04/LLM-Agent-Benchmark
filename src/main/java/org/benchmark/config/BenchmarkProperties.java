package org.benchmark.config;

import lombok.Getter;
import lombok.Setter;
import org.benchmark.model.enums.DocumentComplexity;
import org.benchmark.model.enums.Domain;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed benchmark configuration loaded from {@code application.yml}.
 *
 * <p>Keeping the benchmark settings in a configuration properties class lets Spring
 * handle YAML parsing, enum conversion, and relaxed binding for us.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkProperties {

    private int iterations;
    private int distractorCount;
    private int maxExecutionSteps;
    private int maxExecutionsPerAttempt = 1;
    private int maxRepeatedCommandFailuresPerAttempt = 1;
    private double temperature;
    private int timeoutSeconds;
    private Long randomSeed;
    private Domain domain;
    private DocumentComplexity documentComplexity = DocumentComplexity.CLEAN;
    private boolean trapCommand = false;
    private final LlmProperties llm = new LlmProperties();
    private final PromptProperties prompt = new PromptProperties();

    /**
     * Model connection settings used by the benchmark.
     */
    @Getter
    @Setter
    public static class LlmProperties {
        private String model;
        private String baseUrl;
        private String username;
        private String password;
    }

    /**
     * Prompt templates used during benchmark execution.
     */
    @Getter
    @Setter
    public static class PromptProperties {
        private String baseSystemPrompt;
        private String attemptGuardrails;
    }
}
