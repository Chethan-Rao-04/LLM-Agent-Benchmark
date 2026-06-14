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

    @Bean
    public Random benchmarkRandom(BenchmarkProperties properties) {
        Long seed = properties.getRandomSeed();
        return seed == null ? new Random() : new Random(seed);
    }

    @Bean
    public CommandDict commandDict(Random benchmarkRandom) {
        return new CommandDict(benchmarkRandom);
    }

    @Bean
    public ScenarioLoader scenarioLoader() {
        return new ScenarioLoader();
    }

    @Bean
    public ScenarioResolver scenarioResolver(Random benchmarkRandom) {
        return new ScenarioResolver(benchmarkRandom);
    }

    @Bean
    public ScenarioToolGenerator scenarioToolGenerator(Random benchmarkRandom, CommandDict commandDict) {
        return new ScenarioToolGenerator(benchmarkRandom, commandDict);
    }

    @Bean
    public SemanticDecoyGenerator semanticDecoyGenerator(Random benchmarkRandom, CommandDict commandDict) {
        return new SemanticDecoyGenerator(benchmarkRandom, commandDict);
    }

    @Bean
    public ToolSpecGenerator toolSpecGenerator(Random benchmarkRandom) {
        return new ToolSpecGenerator(benchmarkRandom);
    }

    @Bean
    public DocumentationGenerator documentationGenerator() {
        return new DocumentationGenerator();
    }

    @Bean
    public UserQueryGenerator userQueryGenerator(Random benchmarkRandom) {
        return new UserQueryGenerator(benchmarkRandom);
    }

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
