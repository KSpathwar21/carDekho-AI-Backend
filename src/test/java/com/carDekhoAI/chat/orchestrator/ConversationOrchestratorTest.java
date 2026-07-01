package com.carDekhoAI.chat.orchestrator;

import com.carDekhoAI.chat.agent.ConversationAgent;
import com.carDekhoAI.chat.dto.ChatResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.store.ConversationStore;
import com.carDekhoAI.preference.agent.PreferenceAgent;
import com.carDekhoAI.preference.dto.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationOrchestratorTest {

    private final ConversationStore conversationStore = mock(ConversationStore.class);
    private final ConversationAgent conversationAgent = mock(ConversationAgent.class);
    private final PreferenceAgent preferenceAgent = mock(PreferenceAgent.class);
    private final ConversationOrchestrator orchestrator =
            new ConversationOrchestrator(conversationStore, conversationAgent, preferenceAgent);

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        when(conversationStore.findById("conv-1")).thenReturn(Optional.of(conversation));
    }

    @Test
    void throwsWhenConversationNotFound() {
        when(conversationStore.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.handleMessage("missing", "hi"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void asksNextQuestionWhenPreferencesIncomplete() {
        UserPreference incomplete = new UserPreference(
                1800000L, null, null, null, null, null, null, null, null, null);
        when(preferenceAgent.extract(conversation)).thenReturn(incomplete);
        when(conversationAgent.nextQuestion(conversation, incomplete)).thenReturn("What fuel type?");

        ChatResponse response = orchestrator.handleMessage("conv-1", "Around 18 lakh budget");

        assertThat(response.completed()).isFalse();
        assertThat(response.assistantMessage()).isEqualTo("What fuel type?");
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
        verify(conversationStore).save(conversation);
    }

    @Test
    void completesConversationWhenPreferencesComplete() {
        UserPreference complete = new UserPreference(
                1800000L, "Petrol", "SUV", "Automatic", "City", 4, "Safety", null, null, null);
        when(preferenceAgent.extract(conversation)).thenReturn(complete);

        ChatResponse response = orchestrator.handleMessage("conv-1", "Safety is my top priority");

        assertThat(response.completed()).isTrue();
        assertThat(response.assistantMessage()).contains("budget", "Petrol", "SUV");
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        verify(conversationAgent, never()).nextQuestion(any(), any());
        verify(conversationStore).save(conversation);
    }
}
