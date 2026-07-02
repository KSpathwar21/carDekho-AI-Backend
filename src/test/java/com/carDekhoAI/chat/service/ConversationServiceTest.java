package com.carDekhoAI.chat.service;

import com.carDekhoAI.chat.dto.ConversationResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.chat.store.ConversationStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    private final ConversationStore conversationStore = new ConversationStore();
    private final ConversationService conversationService = new ConversationService(conversationStore);

    @Test
    void startConversationReturnsGreetingAndUniqueId() {
        ConversationResponse first = conversationService.startConversation();
        ConversationResponse second = conversationService.startConversation();

        assertThat(first.conversationId()).isNotBlank();
        assertThat(first.assistantMessage())
                .isEqualTo("Hi! I'm your AI Car Buying Assistant. "
                        + "To recommend the perfect car, please tell me: your budget, preferred fuel type, "
                        + "body type, transmission, typical driving pattern (city, highway, or off-road), "
                        + "family size, and what matters most to you (e.g. safety, mileage, budget, or space).");
        assertThat(second.conversationId()).isNotEqualTo(first.conversationId());
    }

    @Test
    void startConversationPersistsConversationInStore() {
        ConversationResponse response = conversationService.startConversation();

        Optional<Conversation> stored = conversationStore.findById(response.conversationId());

        assertThat(stored).isPresent();
        assertThat(stored.get().getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
        assertThat(stored.get().getMessages()).hasSize(1);
        assertThat(stored.get().getMessages().get(0).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(stored.get().getMessages().get(0).content()).isEqualTo(response.assistantMessage());
    }
}
