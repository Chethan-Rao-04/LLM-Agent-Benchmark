package org.benchmark.app;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the benchmark application.
 *
 * <p>The application hosts the loopback MCP server, configures the remote LLM
 * client, and runs the autonomous benchmark at startup.</p>
 */
@SpringBootApplication(scanBasePackages = "org.benchmark")
@ConfigurationPropertiesScan(basePackages = "org.benchmark")
public class BenchmarkApplication {

    /**
     * Launches the benchmark application.
     *
     * @param args standard JVM application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }
}
