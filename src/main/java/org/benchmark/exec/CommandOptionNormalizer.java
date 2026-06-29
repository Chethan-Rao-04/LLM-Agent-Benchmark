package org.benchmark.exec;

public final class CommandOptionNormalizer {

    private static final String NO_OPTION_LITERAL = "\"\"";

    private CommandOptionNormalizer() {
    }

    public static String normalize(String option) {
        if (option == null) {
            return "";
        }
        String trimmed = option.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return switch (trimmed.toLowerCase()) {
            case "<none>", "none", "null", "\"\"", "''", "-" -> "";
            default -> trimmed;
        };
    }

    public static String formatForModel(String option) {
        return normalize(option).isEmpty() ? NO_OPTION_LITERAL : normalize(option);
    }
}
