package org.benchmark.llm;

import lombok.RequiredArgsConstructor;
import org.benchmark.config.BenchmarkProperties;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Appends shared runtime guardrails to the system prompt.
 *
 * <p>Guardrail text is configuration-driven so benchmark policy can evolve without
 * changing prompt wrapper code.</p>
 */
@Component
@RequiredArgsConstructor
public class BenchmarkGuardrailPromptAdvisor implements CallAdvisor {

    private final BenchmarkProperties properties;

    /**
     * Intercepts the outgoing chat request so benchmark guardrails are appended to the system prompt.
     *
     * @param chatClientRequest request prepared by the caller
     * @param callAdvisorChain remaining advisor chain
     * @return response from the next advisor or model call
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Prompt prompt = advise(chatClientRequest.prompt());
        return callAdvisorChain.nextCall(chatClientRequest.mutate().prompt(prompt).build());
    }

    /**
     * Returns the advisor name that appears in Spring AI diagnostics.
     *
     * @return stable advisor identifier
     */
    @Override
    public String getName() {
        return "benchmark-guardrail-prompt-advisor";
    }

    /**
     * Runs this advisor before later prompt mutators.
     *
     * @return advisor order value
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Appends configured guardrail text to the system message when guardrails are enabled.
     *
     * @param prompt prompt assembled for the current benchmark attempt
     * @return prompt with appended guardrails, or the original prompt when no guardrails are configured
     */
    public Prompt advise(Prompt prompt) {
        String guardrailText = properties.getPrompt().getAttemptGuardrails();
        if (guardrailText == null || guardrailText.isBlank()) {
            return prompt;
        }

        String renderedGuardrails = new PromptTemplate(guardrailText).render(Map.of(
                "maxExecutionsPerAttempt", properties.getMaxExecutionsPerAttempt()
        ));
        String systemText = prompt.getSystemMessage().getText();
        String separator = systemText.isBlank() ? "" : System.lineSeparator();
        String advisedSystemText = systemText + separator + "[GUARDRAILS]: " + renderedGuardrails.trim();
        return prompt.augmentSystemMessage(advisedSystemText);
    }
}
