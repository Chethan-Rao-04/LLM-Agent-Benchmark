package org.benchmark.app;

import lombok.RequiredArgsConstructor;
import org.benchmark.config.BenchmarkProperties;
import org.benchmark.exec.SessionStateManager;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds benchmark prompts from stable templates so executor flow does not own prompt formatting.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkPromptBuilder {

    private static final String SYSTEM_TEMPLATE = """
            {baseSystemPrompt}
            Session ID: {sessionId}
            [CURRENT STATE]: {currentState}
            [GUARDRAILS]: Do not repeat a command that already failed in this attempt. Respect preconditions exactly. Use at most {maxExecutionsPerAttempt} tool calls in one attempt. If a command fails, inspect the state and choose the missing prerequisite instead of retrying blindly.
            [HISTORY]:{conversationHistory}
            """;

    private static final String USER_TEMPLATE = """
            Benchmark session: {sessionId}
            User goal: {userQuery}
            """;

    private final BenchmarkProperties properties;
    private final SessionStateManager stateManager;

    public String buildSystemPrompt(String sessionId, String conversationHistory) {
        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_TEMPLATE);
        return promptTemplate.render(Map.of(
                "baseSystemPrompt", properties.getPrompt().getBaseSystemPrompt(),
                "sessionId", sessionId,
                "currentState", stateManager.getSessionStateSnapshot(sessionId),
                "maxExecutionsPerAttempt", properties.getMaxExecutionsPerAttempt(),
                "conversationHistory", conversationHistory == null ? "" : conversationHistory
        ));
    }

    public String buildUserPrompt(String sessionId, String userQuery) {
        PromptTemplate promptTemplate = new PromptTemplate(USER_TEMPLATE);
        return promptTemplate.render(Map.of(
                "sessionId", sessionId,
                "userQuery", userQuery == null ? "" : userQuery
        ));
    }
}
