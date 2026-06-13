package org.benchmark.llm;

/**
 * Signals that the model transport or provider reported a retryable failure.
 */
public class LlmTransientException extends LlmClientException {

    public LlmTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
