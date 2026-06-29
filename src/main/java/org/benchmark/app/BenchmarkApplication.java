package org.benchmark.app;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

/**
 * Spring Boot entry point for the benchmark application.
 *
 * <p>The application configures the model client, binds generated tools to Spring AI
 * callbacks, and runs the autonomous benchmark at startup.</p>
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
        SpringApplication application = new SpringApplication(BenchmarkApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
