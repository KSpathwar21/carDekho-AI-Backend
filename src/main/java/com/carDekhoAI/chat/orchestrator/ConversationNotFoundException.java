package com.carDekhoAI.chat.orchestrator;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(String conversationId) {
        super("Conversation not found with id: " + conversationId);
    }
}
