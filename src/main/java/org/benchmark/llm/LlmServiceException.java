package org.benchmark.llm;

/**
 * Signals that the model service could not complete a request successfully.
 */
public class LlmServiceException extends LlmClientException {

    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
