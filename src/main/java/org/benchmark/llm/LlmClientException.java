package org.benchmark.llm;

/**
 * Base exception for failures while invoking the configured chat model.
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
