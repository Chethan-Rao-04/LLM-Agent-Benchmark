package org.benchmark.llm;

/**
 * Signals that the model attempted to execute a tool that is not available in the current session.
 */
public class LlmToolCallbackException extends LlmClientException {

    /**
     * Creates an exception for tool-callback mismatches surfaced by Spring AI.
     *
     * @param message failure summary for benchmark retry logic
     * @param cause underlying callback resolution exception
     */
    public LlmToolCallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
