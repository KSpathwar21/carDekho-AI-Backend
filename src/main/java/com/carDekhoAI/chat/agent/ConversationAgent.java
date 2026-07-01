package com.carDekhoAI.chat.agent;

import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import org.springframework.stereotype.Component;

@Component
public class ConversationAgent {

    private static final String SYSTEM_PROMPT = "You are an automotive consultant. "
            + "Continue the conversation. Ask only ONE question. Never ask duplicate questions.";

    private final LlmClient llmClient;

    public ConversationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String nextQuestion(Conversation conversation, UserPreference preferences) {
        String userPrompt = conversation.toTranscript()
                + "\nStill need to ask about: " + describeMissingFields(preferences);
        return llmClient.call(conversation.getConversationId(), SYSTEM_PROMPT, userPrompt);
    }

    private String describeMissingFields(UserPreference preferences) {
        StringBuilder missing = new StringBuilder();
        if (preferences.budget() == null) {
            missing.append("budget, ");
        }
        if (preferences.fuelType() == null) {
            missing.append("fuel type, ");
        }
        if (preferences.bodyType() == null) {
            missing.append("body type, ");
        }
        if (preferences.transmission() == null) {
            missing.append("transmission, ");
        }
        if (preferences.drivingPattern() == null) {
            missing.append("driving pattern, ");
        }
        if (preferences.familySize() == null) {
            missing.append("family size, ");
        }
        if (preferences.priority() == null) {
            missing.append("top priority, ");
        }
        return missing.isEmpty() ? "nothing" : missing.substring(0, missing.length() - 2);
    }
}
