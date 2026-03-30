package org.benchmark.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaConnectionTest {

    @Test
    void shouldReachConfiguredOllamaGenerateEndpoint() throws Exception {
        String user = System.getenv("LLM_USERNAME");;
        String password = System.getenv("LLM_PASSWORD");
        String llmServerUrl = System.getenv("LLM_SERVER_URL");
        String llmModelName = System.getenv("LLM_MODEL_NAME");

        Assumptions.assumeTrue(user != null && !user.isBlank(),
                "Skipping: LLM_USERNAME environment variable not set");
        Assumptions.assumeTrue(password != null && !password.isBlank(),
                "Skipping: LLM_PASSWORD environment variable not set");
        Assumptions.assumeTrue(llmServerUrl != null && !llmServerUrl.isBlank(),
                "Skipping: LLM_SERVER_URL environment variable not set");
        Assumptions.assumeTrue(llmModelName != null && !llmModelName.isBlank(),
                "Skipping: LLM_MODEL_NAME environment variable not set");

        String llmTestPrompt = "What does LLM mean?";

        String credentials = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

        String requestBody = """
                {
                  "model": "%s",
                  "prompt": "%s"
                }
                """.formatted(llmModelName, llmTestPrompt);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmServerUrl))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());

        assertEquals(
                200,
                response.statusCode(),
                "Expected HTTP 200 from generate endpoint. If this fails with 401, check LLM_USERNAME/LLM_PASSWORD environment variables."
        );
    }
}
