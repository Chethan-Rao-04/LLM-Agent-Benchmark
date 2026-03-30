package org.benchmark.app;

import org.benchmark.config.Config;
import org.benchmark.exec.SessionStateManager;
import org.benchmark.gen.BenchmarkCaseGenerator;
import org.benchmark.llm.LlmClient;
import org.benchmark.mcp.runtime.BenchmarkCaseToolCallbackFactory;
import org.benchmark.app.BenchmarkCaseExecutor.BenchmarkScore;
import org.benchmark.utils.RunEventLogger;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The primary entry point for the benchmark application. It coordinates the
 * entire benchmark lifecycle: generating test cases, spinning up MCP servers,
 * sending prompts to the LLM,
 * managing session states, and collecting telemetry/scores across multiple
 * iterations.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BenchmarkRunner implements ApplicationRunner {

    private final Config config;
    private final LlmClient client;
    private final List<McpSyncClient> mcpSyncClients;
    private final SessionStateManager stateManager;
    private final BenchmarkCaseToolCallbackFactory toolCallbackFactory;
    private final RunEventLogger eventLogger;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            runBenchmark();
        } finally {
            closeLoopbackMcpClients();
            applicationContext.close();
        }
    }

    /**
     * Runs the autonomous execution benchmark for the configured number of
     * iterations.
     */
    public void runBenchmark() {

        // Boot up the internal MCP clients (used by the LLM to inspect tool schemas and
        // documentation) and wrap them in a Spring AI callback provider so they can be
        // sent to the LLM.
        ToolCallbackProvider mcpToolCallbacks = initializeLoopbackMcpClients();

        // Configure the case generator. The 'documentComplexity' controls how
        // convoluted the generated tool
        // documentation will be, testing the LLM's reading comprehension.
        Long seed = config.getBenchmark().getRandomSeed();
        BenchmarkCaseGenerator caseGenerator = new BenchmarkCaseGenerator(
                config.documentComplexity(), seed, config.getBenchmark().isMultiStep());
        log.info("Generating Autonomous Execution Benchmark..");
        eventLogger.log("benchmark_run_started", Map.of(
                "model", config.getLlm().getModel(),
                "iterations", config.getBenchmark().getIterations(),
                "documentComplexity", config.getBenchmark().getDocumentComplexity(),
                "maxRetries", config.getBenchmark().getMaxRetries(),
                "distractorCount", config.getBenchmark().getDistractorCount()));

        // Generate the requested number of synthetic cases (sessions). Each case
        // contains a specific goal and a mix of useful and distracting tools.
        var benchmarkCases = caseGenerator.generateCases(
                config.getBenchmark().getIterations(),
                config.getBenchmark().getDistractorCount(),
                config.getBenchmark().getDomain());
        int totalSuccess = 0;
        int autonomousRecoveries = 0;

        // Instantiate the executor that manages the individual retry-loops and LLM API
        // calls for each case.
        BenchmarkCaseExecutor caseExecutor = new BenchmarkCaseExecutor(
                config, client, stateManager, eventLogger);

        // Run through each case sequentially, ensuring state isolation between runs.
        for (int i = 0; i < benchmarkCases.size(); i++) {
            var benchmarkCase = benchmarkCases.get(i);
            String sessionId = UUID.randomUUID().toString();

            // Register immutable case metadata + initialize mutable runtime tool state.
            stateManager.initializeSession(sessionId, benchmarkCase, benchmarkCase.allTools(),
                    new Random(seed != null ? seed : System.currentTimeMillis()));

            // Combine the global introspection tools (to let the LLM read documentation)
            // with the case-specific execution tools (to let the LLM actually run
            // commands).
            ToolCallbackProvider caseToolCallbacks = combineToolCallbacks(
                    mcpToolCallbacks,
                    toolCallbackFactory.create(sessionId, benchmarkCase));

            // Generate the English prompt that tells the LLM what its goal is for this
            // session.
            String userQuery = benchmarkCase.generateUserQuery(benchmarkCase.targetToolObject());

            // Hand off execution to the executor, which handles the conversational
            // retry-loop until the LLM succeeds or fails.
            BenchmarkScore result = caseExecutor.execute(
                    sessionId, benchmarkCase, caseToolCallbacks, userQuery, i + 1);

            // Update metrics based on result
            if (result.passed()) {
                totalSuccess++;
            }
            if (result.recovery()) {
                autonomousRecoveries++;
            }

            // Wipe the runtime state to guarantee the next benchmark iteration starts witha
            // clean slate.
            stateManager.clearSession(sessionId);
        }

        // Log the final tallies to the console and to the telemetry events file.
        log.info("\nFinal Success Rate: {}/{}", totalSuccess, config.getBenchmark().getIterations());
        log.info("Autonomous Recoveries: {}", autonomousRecoveries);
        eventLogger.log("benchmark_run_completed", Map.of(
                "model", config.getLlm().getModel(),
                "successes", totalSuccess,
                "iterations", config.getBenchmark().getIterations(),
                "autonomousRecoveries", autonomousRecoveries));
    }

    /**
     * Ensures all internal MCP clients are connected and ready to process
     * introspection requests,
     * then wraps them in a unified Provider so the LLM can easily invoke them.
     */
    private ToolCallbackProvider initializeLoopbackMcpClients() {
        for (McpSyncClient mcpSyncClient : mcpSyncClients) {
            if (!mcpSyncClient.isInitialized()) {
                mcpSyncClient.initialize();
            }
        }
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .build();
    }

    /**
     * Merges multiple parallel ToolCallbackProviders into a single static provider
     * list.
     * This builds the final, complete toolkit that is shipped attached to the LLM
     * system prompt.
     */
    private ToolCallbackProvider combineToolCallbacks(ToolCallbackProvider... providers) {
        List<ToolCallback> combined = new java.util.ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            for (ToolCallback toolCallback : provider.getToolCallbacks()) {
                combined.add(toolCallback);
            }
        }
        return new StaticToolCallbackProvider(combined);
    }

    /**
     * Gracefully shuts down the long-running MCP clients to free up server
     * resources before the application exits.
     */
    private void closeLoopbackMcpClients() {
        for (McpSyncClient mcpSyncClient : mcpSyncClients) {
            try {
                if (mcpSyncClient.isInitialized() && !mcpSyncClient.closeGracefully()) {
                    mcpSyncClient.close();
                }
            } catch (Exception ignored) {
                try {
                    mcpSyncClient.close();
                } catch (Exception ignoredAgain) {
                    // Shutdown should not fail because an MCP client was already torn down.
                }
            }
        }
    }
}
