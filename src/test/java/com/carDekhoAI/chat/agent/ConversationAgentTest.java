package com.carDekhoAI.chat.agent;

import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.Message;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationAgentTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ConversationAgent conversationAgent = new ConversationAgent(llmClient);

    @Test
    void nextQuestionIncludesMissingFieldsHintAndReturnsLlmResponse() {
        Conversation conversation = Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        conversation.setMessages(List.of(
                new Message(MessageRole.ASSISTANT, "Hi!", LocalDateTime.now()),
                new Message(MessageRole.USER, "I have around 18 lakh budget", LocalDateTime.now())
        ));
        UserPreference partialPreference = new UserPreference(
                1800000L, null, null, null, null, null, null, null, null, null);

        when(llmClient.call(eq("conv-1"), anyString(), contains("budget")))
                .thenReturn("Do you prefer Petrol, Diesel or Electric?");

        String question = conversationAgent.nextQuestion(conversation, partialPreference);

        assertThat(question).isEqualTo("Do you prefer Petrol, Diesel or Electric?");
        verify(llmClient).call(eq("conv-1"), anyString(),
                contains("Still need to ask about: fuel type"));
    }
}
