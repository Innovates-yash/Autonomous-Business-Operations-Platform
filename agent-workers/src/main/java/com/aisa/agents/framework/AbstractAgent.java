package com.aisa.agents.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Abstract base class for all specialized agents, providing common utilities:
 * <ul>
 *   <li>Prompt template loading from {@code classpath:prompts/<name>.txt}</li>
 *   <li>Placeholder substitution ({@code {{PLACEHOLDER}}} tokens)</li>
 *   <li>JSON parsing and field validation helpers</li>
 * </ul>
 *
 * <p>Subclasses implement {@link #agentType()}, {@link #buildPrompt(Map)},
 * and {@link #processResponse(String)}.
 */
public abstract class AbstractAgent implements SpecializedAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();

    private final String promptTemplate;

    protected AbstractAgent(String promptFileName) {
        this.promptTemplate = loadPromptTemplate(promptFileName);
    }

    /**
     * Load a prompt template from the classpath.
     */
    private String loadPromptTemplate(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + fileName);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: prompts/{}", fileName, e);
            throw new IllegalStateException("Cannot load prompt template: " + fileName, e);
        }
    }

    /**
     * Get the raw prompt template.
     */
    protected String getPromptTemplate() {
        return promptTemplate;
    }

    /**
     * Substitute a placeholder in the prompt template.
     *
     * @param template    the template string
     * @param placeholder the placeholder name (without braces, e.g. "PREREQUISITES")
     * @param value       the value to substitute
     * @return the template with the placeholder replaced
     */
    protected String substitute(String template, String placeholder, String value) {
        return template.replace("{{" + placeholder + "}}", value != null ? value : "");
    }

    /**
     * Extract JSON from a response that may contain markdown code fences or other text.
     * Looks for the first {@code {  ...  }} or {@code [ ... ]} block.
     *
     * @param response the raw AI response text
     * @return the extracted JSON string
     */
    protected String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        // Strip markdown code fences if present
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Find the first { or [
        int objStart = cleaned.indexOf('{');
        int arrStart = cleaned.indexOf('[');

        int start;
        char openChar;
        char closeChar;

        if (objStart == -1 && arrStart == -1) {
            return cleaned; // Return as-is if no JSON structure found
        } else if (objStart == -1) {
            start = arrStart;
            openChar = '[';
            closeChar = ']';
        } else if (arrStart == -1) {
            start = objStart;
            openChar = '{';
            closeChar = '}';
        } else {
            start = Math.min(objStart, arrStart);
            openChar = start == objStart ? '{' : '[';
            closeChar = start == objStart ? '}' : ']';
        }

        // Find matching close
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == openChar) depth++;
                else if (c == closeChar) {
                    depth--;
                    if (depth == 0) {
                        return cleaned.substring(start, i + 1);
                    }
                }
            }
        }

        return cleaned.substring(start); // Return from start if no matching close found
    }

    /**
     * Parse a JSON string into a JsonNode.
     *
     * @param json the JSON string to parse
     * @return the parsed JsonNode
     * @throws AgentValidationException if the JSON is malformed
     */
    protected JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AgentValidationException("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Require a field to exist and be non-null/non-empty in the JSON node.
     *
     * @param node      the parent JSON node
     * @param fieldName the required field name
     * @throws AgentValidationException if the field is missing or empty
     */
    protected void requireField(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            throw new AgentValidationException("Missing required field: " + fieldName);
        }
        JsonNode field = node.get(fieldName);
        if (field.isTextual() && field.asText().isBlank()) {
            throw new AgentValidationException("Field must not be blank: " + fieldName);
        }
    }

    /**
     * Require a field to be a non-empty array.
     *
     * @param node      the parent JSON node
     * @param fieldName the required array field name
     * @throws AgentValidationException if the field is missing, not an array, or empty
     */
    protected void requireNonEmptyArray(JsonNode node, String fieldName) {
        requireField(node, fieldName);
        JsonNode field = node.get(fieldName);
        if (!field.isArray()) {
            throw new AgentValidationException("Field must be an array: " + fieldName);
        }
        if (field.isEmpty()) {
            throw new AgentValidationException("Array must not be empty: " + fieldName);
        }
    }

    /**
     * Require a field to be a non-null object.
     */
    protected void requireObject(JsonNode node, String fieldName) {
        requireField(node, fieldName);
        JsonNode field = node.get(fieldName);
        if (!field.isObject()) {
            throw new AgentValidationException("Field must be an object: " + fieldName);
        }
    }

    /**
     * Exception thrown during agent output validation.
     */
    public static class AgentValidationException extends RuntimeException {
        public AgentValidationException(String message) {
            super(message);
        }
    }
}
