package org.benchmark.app;

import org.springframework.stereotype.Component;

/**
 * Encapsulates retry loop rules so executor code only coordinates work.
 */
@Component
public class BenchmarkRetryPolicy {

    public boolean shouldContinue(int attempt, int maxRetries, boolean goalAchieved) {
        return attempt < maxRetries && !goalAchieved;
    }

    public boolean failedFirstAttempt(int attempt, boolean goalAchieved) {
        return attempt == 1 && !goalAchieved;
    }

    public String appendAttemptHistory(String existingHistory,
                                       int attempt,
                                       String assistantContent,
                                       String attemptFeedback,
                                       int executionsThisAttempt,
                                       int discoveryCallsThisAttempt,
                                       int maxHistoryChars) {
        String historyEntry = "\n[Attempt " + attempt + "] "
                + "assistant=" + compress(assistantContent, 280)
                + " | feedback=" + compress(attemptFeedback, 700)
                + " | executions=" + executionsThisAttempt
                + " | discoveryCalls=" + discoveryCallsThisAttempt;

        String combined = existingHistory + historyEntry;
        if (combined.length() <= maxHistoryChars) {
            return combined;
        }
        return combined.substring(combined.length() - maxHistoryChars);
    }

    private String compress(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= limit ? singleLine : singleLine.substring(0, limit) + "...";
    }
}
