package org.benchmark.config;

import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.gen.doc_generator.DocumentationGenerator;
import org.benchmark.gen.query_generator.UserQueryGenerator;
import org.benchmark.gen.scenario.ScenarioLoader;
import org.benchmark.gen.scenario.ScenarioResolver;
import org.benchmark.gen.tool_generator.CommandDict;
import org.benchmark.gen.tool_generator.ScenarioToolGenerator;
import org.benchmark.gen.tool_generator.SemanticDecoyGenerator;
import org.benchmark.gen.tool_generator.ToolSpecGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

/**
 * Wires the benchmark generation graph through Spring so runner code stays focused on orchestration.
 */
@Configuration
public class BenchmarkGenerationConfig {

    /**
     * Creates the shared random source used by all benchmark generators.
     *
     * @param properties benchmark settings that may provide a fixed seed
     * @return deterministic random source when a seed is configured, otherwise a fresh random source
     */
    @Bean
    public Random benchmarkRandom(BenchmarkProperties properties) {
        Long seed = properties.getRandomSeed();
        return seed == null ? new Random() : new Random(seed);
    }

    /**
     * Exposes the domain vocabulary dictionary used by tool and scenario generators.
     *
     * @param benchmarkRandom shared benchmark random source
     * @return command dictionary backed by the shared random source
     */
    @Bean
    public CommandDict commandDict(Random benchmarkRandom) {
        return new CommandDict(benchmarkRandom);
    }

    /**
     * Loads scenario templates from the classpath once at application startup.
     *
     * @return scenario loader backed by the default YAML resource
     */
    @Bean
    public ScenarioLoader scenarioLoader() {
        return new ScenarioLoader();
    }

    /**
     * Resolves scenario template variables into concrete domain-specific values.
     *
     * @param benchmarkRandom shared benchmark random source
     * @return scenario resolver
     */
    @Bean
    public ScenarioResolver scenarioResolver(Random benchmarkRandom) {
        return new ScenarioResolver(benchmarkRandom);
    }

    /**
     * Builds target tools whose commands align with resolved benchmark scenarios.
     *
     * @param benchmarkRandom shared benchmark random source
     * @param commandDict domain vocabulary dictionary
     * @return scenario-aware tool generator
     */
    @Bean
    public ScenarioToolGenerator scenarioToolGenerator(Random benchmarkRandom, CommandDict commandDict) {
        return new ScenarioToolGenerator(benchmarkRandom, commandDict);
    }

    /**
     * Builds semantic decoy tools that look plausible within the same domain vocabulary.
     *
     * @param benchmarkRandom shared benchmark random source
     * @param commandDict domain vocabulary dictionary
     * @return decoy generator
     */
    @Bean
    public SemanticDecoyGenerator semanticDecoyGenerator(Random benchmarkRandom, CommandDict commandDict) {
        return new SemanticDecoyGenerator(benchmarkRandom, commandDict);
    }

    /**
     * Builds random distractor tools that are not tied to a scenario template.
     *
     * @param benchmarkRandom shared benchmark random source
     * @return generic tool specification generator
     */
    @Bean
    public ToolSpecGenerator toolSpecGenerator(Random benchmarkRandom) {
        return new ToolSpecGenerator(benchmarkRandom);
    }

    /**
     * Produces synthetic tool documentation with the configured quality profile.
     *
     * @return documentation generator
     */
    @Bean
    public DocumentationGenerator documentationGenerator() {
        return new DocumentationGenerator();
    }

    /**
     * Builds user requests that express only the intended outcome of a scenario.
     *
     * @param benchmarkRandom shared benchmark random source
     * @return goal-oriented query generator
     */
    @Bean
    public UserQueryGenerator userQueryGenerator(Random benchmarkRandom) {
        return new UserQueryGenerator(benchmarkRandom);
    }

    /**
     * Assembles the top-level benchmark case generator from its scenario, tool, and prompt collaborators.
     *
     * @param properties benchmark configuration properties
     * @param scenarioLoader scenario template loader
     * @param scenarioResolver scenario resolver
     * @param scenarioToolGenerator scenario-aligned tool generator
     * @param semanticDecoyGenerator semantic distractor generator
     * @param toolSpecGenerator random distractor generator
     * @param documentationGenerator documentation renderer
     * @param userQueryGenerator user query generator
     * @param benchmarkRandom shared benchmark random source
     * @return fully configured benchmark case generator
     */
    @Bean
    public BenchmarkCaseGenerator benchmarkCaseGenerator(BenchmarkProperties properties,
                                                         ScenarioLoader scenarioLoader,
                                                         ScenarioResolver scenarioResolver,
                                                         ScenarioToolGenerator scenarioToolGenerator,
                                                         SemanticDecoyGenerator semanticDecoyGenerator,
                                                         ToolSpecGenerator toolSpecGenerator,
                                                         DocumentationGenerator documentationGenerator,
                                                         UserQueryGenerator userQueryGenerator,
                                                         Random benchmarkRandom) {
        return new BenchmarkCaseGenerator(
                benchmarkRandom,
                scenarioLoader,
                scenarioResolver,
                scenarioToolGenerator,
                semanticDecoyGenerator,
                toolSpecGenerator,
                documentationGenerator,
                userQueryGenerator,
                properties.getDocumentComplexity(),
                properties.isTrapCommand()
        );
    }
}
