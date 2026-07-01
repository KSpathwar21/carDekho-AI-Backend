package com.carDekhoAI.preference.agent;

import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PreferenceAgent {

    private static final String SYSTEM_PROMPT = """
            Extract structured JSON representing the user's car-buying preferences \
            from the conversation so far. Return ONLY JSON. No explanation.

            JSON schema (use null for anything not yet mentioned):
            {
              "budget": number or null,
              "fuelType": string or null,
              "bodyType": string or null,
              "transmission": string or null,
              "drivingPattern": string or null,
              "familySize": number or null,
              "priority": string or null,
              "brandPreference": string or null,
              "groundClearance": number or null,
              "bootSpace": number or null
            }""";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public PreferenceAgent(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public UserPreference extract(Conversation conversation) {
        String raw = llmClient.call(conversation.getConversationId(), SYSTEM_PROMPT, conversation.toTranscript());
        String json = stripMarkdownFences(raw);
        try {
            return objectMapper.readValue(json, UserPreference.class);
        } catch (JsonProcessingException e) {
            throw new PreferenceExtractionException("Failed to parse preference JSON: " + raw, e);
        }
    }

    private String stripMarkdownFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceFirst("```\\s*$", "");
        }
        return trimmed.trim();
    }
}
