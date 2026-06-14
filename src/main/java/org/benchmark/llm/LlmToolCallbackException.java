package org.benchmark.llm;

/**
 * Signals that the model attempted to execute a tool that is not available in the current session.
 */
public class LlmToolCallbackException extends LlmClientException {

    public LlmToolCallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
