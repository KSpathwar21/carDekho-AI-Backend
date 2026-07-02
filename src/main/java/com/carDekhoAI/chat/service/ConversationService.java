package com.carDekhoAI.chat.service;

import com.carDekhoAI.chat.dto.ConversationResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.Message;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.chat.store.ConversationStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConversationService {

    private static final String GREETING = "Hi! I'm your AI Car Buying Assistant. "
            + "To recommend the perfect car, please tell me: your budget, preferred fuel type, "
            + "body type, transmission, typical driving pattern (city, highway, or off-road), "
            + "family size, and what matters most to you (e.g. safety, mileage, budget, or space).";

    private final ConversationStore conversationStore;

    public ConversationService(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    public ConversationResponse startConversation() {
        Message greeting = new Message(MessageRole.ASSISTANT, GREETING, LocalDateTime.now());

        Conversation conversation = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .status(ConversationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        conversation.getMessages().add(greeting);

        conversationStore.save(conversation);

        return new ConversationResponse(conversation.getConversationId(), greeting.content());
    }
}
