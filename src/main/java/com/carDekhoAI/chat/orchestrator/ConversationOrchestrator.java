package com.carDekhoAI.chat.orchestrator;

import com.carDekhoAI.chat.agent.ConversationAgent;
import com.carDekhoAI.chat.dto.ChatResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.Message;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.chat.store.ConversationStore;
import com.carDekhoAI.preference.agent.PreferenceAgent;
import com.carDekhoAI.preference.dto.UserPreference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ConversationOrchestrator {

    private final ConversationStore conversationStore;
    private final ConversationAgent conversationAgent;
    private final PreferenceAgent preferenceAgent;

    public ConversationOrchestrator(ConversationStore conversationStore,
                                     ConversationAgent conversationAgent,
                                     PreferenceAgent preferenceAgent) {
        this.conversationStore = conversationStore;
        this.conversationAgent = conversationAgent;
        this.preferenceAgent = preferenceAgent;
    }

    public ChatResponse handleMessage(String conversationId, String userMessage) {
        Conversation conversation = conversationStore.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        conversation.getMessages().add(new Message(MessageRole.USER, userMessage, LocalDateTime.now()));

        UserPreference preferences = preferenceAgent.extract(conversation);
        conversation.setPreferences(preferences);

        String assistantMessage;
        boolean completed = preferences.isComplete();
        if (completed) {
            assistantMessage = buildCompletionMessage(preferences);
            conversation.setStatus(ConversationStatus.COMPLETED);
        } else {
            assistantMessage = conversationAgent.nextQuestion(conversation, preferences);
        }

        conversation.getMessages().add(new Message(MessageRole.ASSISTANT, assistantMessage, LocalDateTime.now()));
        conversationStore.save(conversation);

        return new ChatResponse(assistantMessage, completed);
    }

    private String buildCompletionMessage(UserPreference preferences) {
        return "Great, I have everything I need! Here's what I've got: budget ₹" + preferences.budget()
                + ", " + preferences.fuelType() + " fuel, " + preferences.bodyType() + " body type, "
                + preferences.transmission() + " transmission, mostly " + preferences.drivingPattern()
                + " driving, family of " + preferences.familySize() + ", and your top priority is "
                + preferences.priority() + ". I'll put together some recommendations for you next.";
    }
}
