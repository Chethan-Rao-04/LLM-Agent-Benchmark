package org.benchmark.llm;

/**
 * Signals that the model transport or provider reported a retryable failure.
 */
public class LlmTransientException extends LlmClientException {

    /**
     * Creates an exception that marks a model failure as retryable.
     *
     * @param message retry-oriented failure summary
     * @param cause underlying transient exception
     */
    public LlmTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
