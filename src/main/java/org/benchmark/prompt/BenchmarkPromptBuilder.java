package org.benchmark.prompt;

import lombok.RequiredArgsConstructor;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds benchmark prompts from stable templates so executor flow does not own prompt formatting.
 *
 * <p>Prompt rendering stays here so benchmark policy, state injection, and template resources
 * can evolve without spreading prompt-shaping logic across the retry loop.</p>
 */
@Component
@RequiredArgsConstructor
public class BenchmarkPromptBuilder {

    private static final Resource SYSTEM_TEMPLATE_RESOURCE =
            new ClassPathResource("prompts/benchmark-system.st");
    private static final Resource USER_TEMPLATE_RESOURCE = new ClassPathResource("prompts/benchmark-user.st");
    private static final Resource RETRY_TEMPLATE_RESOURCE = new ClassPathResource("prompts/benchmark-retry.st");

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;

    public List<Message> buildInitialMessages(String sessionId, String userQuery) {
        return List.of(
                new SystemMessage(renderTemplate(SYSTEM_TEMPLATE_RESOURCE, Map.of(
                        "baseSystemPrompt", properties.getPrompt().getBaseSystemPrompt(),
                        "caseManual", stateManager.requireBenchmarkCase(sessionId).caseManual()
                ))),
                new UserMessage(renderTemplate(USER_TEMPLATE_RESOURCE, Map.of(
                        "sessionId", sessionId,
                        "userQuery", userQuery == null ? "" : userQuery,
                        "currentState", stateManager.getSessionStateSnapshot(sessionId).toString()
                )))
        );
    }

    public UserMessage buildRetryMessage(String sessionId,
                                         int attempt,
                                         String attemptFeedback) {
        return new UserMessage(renderTemplate(RETRY_TEMPLATE_RESOURCE, Map.of(
                "attempt", attempt,
                "attemptFeedback", attemptFeedback == null ? "" : attemptFeedback,
                "currentState", stateManager.getSessionStateSnapshot(sessionId).toString()
        )));
    }

    private String renderTemplate(Resource templateResource, Map<String, Object> variables) {
        PromptTemplate promptTemplate = new PromptTemplate(templateResource);
        return promptTemplate.render(variables);
    }
}
