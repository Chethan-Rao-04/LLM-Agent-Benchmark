package org.benchmark.llm;

import org.benchmark.config.Config;
import org.junit.jupiter.api.Test;

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
        Config config = Config.load();

        String user = "team2025-6"; //config.getLlm().getUsername();
        String password = "8saNPufwZqavzuMc"; // config.getLlm().getPassword();
        String llmServerUrl = "http://gpu6.fin.uni-magdeburg.de/api/generate";///api/generate".formatted(config.getLlm().getBaseUrl().replaceAll("/+$", ""));
        String llmModelName = "gpt-oss:20b"; //config.getLlm().getModel();
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
                "Expected HTTP 200 from /api/generate. If this fails with 401, update llm.username/llm.password "
                        + "in src/main/java/org/benchmark/config.yaml or override them with LLM_USERNAME/LLM_PASSWORD."
        );
    }
}
