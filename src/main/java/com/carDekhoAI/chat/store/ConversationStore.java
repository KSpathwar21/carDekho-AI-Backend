package com.carDekhoAI.chat.store;

import com.carDekhoAI.chat.model.Conversation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationStore {

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public Conversation save(Conversation conversation) {
        conversations.put(conversation.getConversationId(), conversation);
        return conversation;
    }

    public Optional<Conversation> findById(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }
}
