package org.benchmark.llm;

/**
 * Signals that the model service could not complete a request successfully.
 */
public class LlmServiceException extends LlmClientException {

    /**
     * Creates an exception for non-retryable model service failures.
     *
     * @param message benchmark-facing failure summary
     * @param cause underlying model or client exception
     */
    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
