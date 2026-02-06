package org.benchmark.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;


public class ResponseParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedToolCall parse(String content) {
        try {

            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            } else if (content.contains("```")) {
                content = content.replace("```", "");
            }
            content = content.trim();
            // Takes the LLM response (string) (expected JSON),
            // converts into a JSON tree structure, and fetches the root rootNode.
            JsonNode rootNode = mapper.readTree(content);

            // Fetch the tool,command,option from the JSON tree, using root (converts them as text)
            String toolName = rootNode.has("tool") ? rootNode.get("tool").asText() : null;
            String commandName = rootNode.has("command") ? rootNode.get("command").asText() : null;

            String option = null;


            // 2. Safe Option Parsing
            if (rootNode.has("option") && rootNode.get("option").isTextual()) {
                JsonNode optionNode = rootNode.get("option");

                    // Handle case where LLM returns single string instead of array
                    String text = optionNode.asText();
                    if (text != null && !text.trim().isEmpty()) {
                        option = text;
                    }
                }
            else {
                option = null;
                System.out.println("Option field is missing or not a string.");
            }    
            


            boolean hasToolAndCmdName = toolName != null && commandName != null;

            return new ParsedToolCall(toolName, commandName, option, hasToolAndCmdName);
        } catch (Exception e) {
            return new ParsedToolCall(null, null, null, false);
        }
    }

    @Getter
    public static class ParsedToolCall {

        private final String toolName;
        private final String commandName;
        private final String option;
        private final boolean hasToolAndCmdName;

        // Constructors
        public ParsedToolCall(String toolName, String commandName, String option, boolean hasToolAndCmdName) {
            this.toolName = toolName;
            this.commandName = commandName;
            this.option = option;
            this.hasToolAndCmdName = hasToolAndCmdName;
        }

    }
}