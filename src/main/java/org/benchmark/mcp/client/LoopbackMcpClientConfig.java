package org.benchmark.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.benchmark.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for the loopback MCP client used by the benchmark runner.
 *
 * <p>The benchmark host exposes an MCP server locally and also connects back to
 * it through this client so Spring AI can access the tool surface as callbacks.</p>
 */
@Configuration
public class LoopbackMcpClientConfig {

    /**
     * Creates the loopback {@link McpSyncClient} that connects to the local MCP endpoint.
     *
     * @param config benchmark configuration used to derive timeouts
     * @return configured MCP sync client
     */
    @Bean
    public McpSyncClient loopbackMcpSyncClient(Config config) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:8080/mcp")
                .openConnectionOnStartup(false)
                .build();

        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(config.getBenchmark().getTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(20))
                .build();
    }
}
