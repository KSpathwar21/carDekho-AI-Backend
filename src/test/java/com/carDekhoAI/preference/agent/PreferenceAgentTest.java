package com.carDekhoAI.preference.agent;

import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.Message;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferenceAgentTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final PreferenceAgent preferenceAgent = new PreferenceAgent(llmClient, new ObjectMapper());

    private Conversation sampleConversation() {
        Conversation conversation = Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        conversation.setMessages(List.of(
                new Message(MessageRole.ASSISTANT, "What's your budget?", LocalDateTime.now()),
                new Message(MessageRole.USER, "Around 18 lakh", LocalDateTime.now())
        ));
        return conversation;
    }

    @Test
    void extractsUserPreferenceFromPlainJson() {
        when(llmClient.call(anyString(), anyString(), anyString()))
                .thenReturn("{\"budget\":1800000,\"fuelType\":\"Petrol\",\"bodyType\":null,"
                        + "\"transmission\":null,\"drivingPattern\":null,\"familySize\":null,"
                        + "\"priority\":null,\"brandPreference\":null,\"groundClearance\":null,\"bootSpace\":null}");

        UserPreference preference = preferenceAgent.extract(sampleConversation());

        assertThat(preference.budget()).isEqualTo(1800000L);
        assertThat(preference.fuelType()).isEqualTo("Petrol");
        assertThat(preference.isComplete()).isFalse();
    }

    @Test
    void stripsMarkdownFencesBeforeParsing() {
        when(llmClient.call(anyString(), anyString(), anyString()))
                .thenReturn("```json\n{\"budget\":1800000,\"fuelType\":\"Petrol\",\"bodyType\":\"SUV\","
                        + "\"transmission\":\"Automatic\",\"drivingPattern\":\"City\",\"familySize\":4,"
                        + "\"priority\":\"Safety\",\"brandPreference\":null,\"groundClearance\":null,"
                        + "\"bootSpace\":null}\n```");

        UserPreference preference = preferenceAgent.extract(sampleConversation());

        assertThat(preference.isComplete()).isTrue();
        assertThat(preference.bodyType()).isEqualTo("SUV");
    }

    @Test
    void wrapsMalformedJsonInPreferenceExtractionException() {
        when(llmClient.call(anyString(), anyString(), anyString())).thenReturn("not valid json");

        assertThatThrownBy(() -> preferenceAgent.extract(sampleConversation()))
                .isInstanceOf(PreferenceExtractionException.class);
    }
}
