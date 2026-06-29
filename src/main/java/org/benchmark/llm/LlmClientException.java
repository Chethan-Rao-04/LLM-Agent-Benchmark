package org.benchmark.llm;

/**
 * Base exception for failures while invoking the configured chat model.
 */
public class LlmClientException extends RuntimeException {

    /**
     * Creates a benchmark-specific wrapper around lower-level model invocation failures.
     *
     * @param message high-level failure summary for benchmark code
     * @param cause underlying transport or provider exception
     */
    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
