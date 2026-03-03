package org.benchmark.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ToolCallParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedToolCall parse(String content) {
        try {
            // Robust extraction: Locate the JSON object within the content
            // This handles cases where the LLM wraps JSON in Markdown or adds conversational text
            int firstBrace = content.indexOf('{');
            int lastBrace = content.lastIndexOf('}');

            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                content = content.substring(firstBrace, lastBrace + 1);
            } else {
                // Fallback cleanup if braces aren't clear/present
                if (content.contains("```json")) {
                    content = content.replace("```json", "").replace("```", "");
                } else if (content.contains("```")) {
                    content = content.replace("```", "");
                }
            }
            content = content.trim();

            // Takes the LLM response (string) (expected JSON),
            // converts into a JSON tree structure, and fetches the root rootNode.
            JsonNode rootNode = mapper.readTree(content);

            // Fetch the tool, command, option from the JSON tree, using root (converts them as text)
            String toolName = rootNode.has("tool") ? rootNode.get("tool").asText() : null;
            String commandName = rootNode.has("command") ? rootNode.get("command").asText() : null;

            String option = null;

            // 2. Safe Option Parsing
            if (rootNode.has("option") && rootNode.get("option").isTextual()) {
                JsonNode optionNode = rootNode.get("option");

                // Handle case where LLM returns single string
                String text = optionNode.asText();
                // Treat whitespace-only strings as null (no option)
                if (text != null && !text.trim().isEmpty()) {
                    option = text;
                }
            } else {
                // Valid scenario: option might be missing if the command doesn't require one
                option = null;
            }

            boolean hasToolAndCmdName = toolName != null && commandName != null;

            return new ParsedToolCall(toolName, commandName, option, hasToolAndCmdName);
        } catch (Exception e) {
            // Return a failed parse result
            return new ParsedToolCall(null, null, null, false);
        }
    }

    public record ParsedToolCall(String toolName, String commandName, String option, boolean hasToolAndCmdName) {}
}
