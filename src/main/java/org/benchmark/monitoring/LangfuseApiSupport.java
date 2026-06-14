package org.benchmark.monitoring;

import org.benchmark.config.BenchmarkProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared Langfuse HTTP helper methods to keep auth and URL handling consistent.
 */
public final class LangfuseApiSupport {

    private LangfuseApiSupport() {
    }

    public static String basicAuthHeader(BenchmarkProperties.LangfuseProperties langfuse) {
        String credentials = nullSafe(langfuse.getPublicKey()) + ":" + nullSafe(langfuse.getSecretKey());
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public static String apiUrl(BenchmarkProperties.LangfuseProperties langfuse, String path) {
        String normalizedBaseUrl = normalizeBaseUrl(langfuse.getBaseUrl());
        if (path.startsWith("/")) {
            return normalizedBaseUrl + path;
        }
        return normalizedBaseUrl + "/" + path;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = nullSafe(baseUrl).trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
