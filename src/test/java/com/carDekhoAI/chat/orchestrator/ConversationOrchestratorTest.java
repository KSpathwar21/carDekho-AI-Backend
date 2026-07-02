package com.carDekhoAI.chat.orchestrator;

import com.carDekhoAI.car.entity.BodyType;
import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.entity.FuelType;
import com.carDekhoAI.car.entity.Transmission;
import com.carDekhoAI.car.tool.DatabaseTool;
import com.carDekhoAI.chat.dto.ChatResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.chat.store.ConversationStore;
import com.carDekhoAI.preference.agent.PreferenceAgent;
import com.carDekhoAI.preference.dto.UserPreference;
import com.carDekhoAI.recommendation.agent.RecommendationAgent;
import com.carDekhoAI.sql.agent.SqlAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationOrchestratorTest {

    private final ConversationStore conversationStore = mock(ConversationStore.class);
    private final PreferenceAgent preferenceAgent = mock(PreferenceAgent.class);
    private final SqlAgent sqlAgent = mock(SqlAgent.class);
    private final DatabaseTool databaseTool = mock(DatabaseTool.class);
    private final RecommendationAgent recommendationAgent = mock(RecommendationAgent.class);
    private final ConversationOrchestrator orchestrator = new ConversationOrchestrator(
            conversationStore, preferenceAgent, sqlAgent, databaseTool, recommendationAgent);

    private Conversation conversation;
    private final UserPreference completePreferences = new UserPreference(
            1800000L, "Petrol", "SUV", "Automatic", "City", 4, "Safety", null, null, null);

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .conversationId("conv-1")
                .status(ConversationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        when(conversationStore.findById("conv-1")).thenReturn(Optional.of(conversation));
    }

    private Car car(long id, String brand) {
        return Car.builder()
                .id(id)
                .brand(brand)
                .model("Model")
                .variant("Variant")
                .bodyType(BodyType.SUV)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .price(1500000L)
                .build();
    }

    @Test
    void throwsWhenConversationNotFound() {
        when(conversationStore.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.handleMessage("missing", "hi"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void buildsDeterministicFollowUpWhenPreferencesIncomplete() {
        UserPreference incomplete = new UserPreference(
                1800000L, null, null, null, null, null, null, null, null, null);
        when(preferenceAgent.extract(conversation)).thenReturn(incomplete);

        ChatResponse response = orchestrator.handleMessage("conv-1", "Around 18 lakh budget");

        assertThat(response.completed()).isFalse();
        assertThat(response.assistantMessage()).isEqualTo(
                "Thanks! I still need a few more details: fuel type, body type, transmission, "
                        + "driving pattern, family size, top priority. Could you share those?");
        assertThat(response.recommendations()).isEmpty();
        assertThat(response.comparison()).isEmpty();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
        verify(conversationStore).save(conversation);
        verify(preferenceAgent, times(1)).extract(conversation);
        verifyNoInteractions(sqlAgent, databaseTool, recommendationAgent);
    }

    @Test
    void appendsUserAndAssistantMessagesToConversationTranscript() {
        UserPreference incomplete = new UserPreference(
                1800000L, null, null, null, null, null, null, null, null, null);
        when(preferenceAgent.extract(conversation)).thenReturn(incomplete);

        orchestrator.handleMessage("conv-1", "Around 18 lakh budget");

        assertThat(conversation.getMessages()).hasSize(2);
        assertThat(conversation.getMessages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(conversation.getMessages().get(0).content()).isEqualTo("Around 18 lakh budget");
        assertThat(conversation.getMessages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(conversation.getMessages().get(1).content()).isEqualTo(
                "Thanks! I still need a few more details: fuel type, body type, transmission, "
                        + "driving pattern, family size, top priority. Could you share those?");
    }

    @Test
    void completesWithRecommendationsWhenCarsFound() {
        when(preferenceAgent.extract(conversation)).thenReturn(completePreferences);
        when(sqlAgent.generateValidatedSql("conv-1", completePreferences))
                .thenReturn("SELECT * FROM cars LIMIT 5");
        Car carA = car(1L, "Maruti Suzuki");
        Car carB = car(2L, "Tata");
        List<Car> cars = List.of(carA, carB);
        when(databaseTool.execute("SELECT * FROM cars LIMIT 5")).thenReturn(cars);
        when(recommendationAgent.explain("conv-1", completePreferences, cars, true))
                .thenReturn("## Summary\nGreat picks!");

        ChatResponse response = orchestrator.handleMessage("conv-1", "Safety is my top priority");

        assertThat(response.completed()).isTrue();
        assertThat(response.assistantMessage()).isEqualTo("## Summary\nGreat picks!");
        assertThat(response.recommendations()).hasSize(2);
        assertThat(response.recommendations().get(0).brand()).isEqualTo("Maruti Suzuki");
        assertThat(response.comparison()).isEqualTo(response.recommendations());
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        verify(conversationStore).save(conversation);
        verify(sqlAgent, never()).generateFallbackSql(any(), any());
    }

    @Test
    void completesWithClosestMatchesWhenStrictQueryReturnsNoResults() {
        when(preferenceAgent.extract(conversation)).thenReturn(completePreferences);
        when(sqlAgent.generateValidatedSql("conv-1", completePreferences))
                .thenReturn("SELECT * FROM cars WHERE price <= 100 LIMIT 5");
        when(databaseTool.execute("SELECT * FROM cars WHERE price <= 100 LIMIT 5")).thenReturn(List.of());
        when(sqlAgent.generateFallbackSql("conv-1", completePreferences))
                .thenReturn("SELECT * FROM cars ORDER BY ABS(price - 1800000) LIMIT 5");
        Car closest = car(3L, "Hyundai");
        List<Car> fallbackCars = List.of(closest);
        when(databaseTool.execute("SELECT * FROM cars ORDER BY ABS(price - 1800000) LIMIT 5"))
                .thenReturn(fallbackCars);
        when(recommendationAgent.explain("conv-1", completePreferences, fallbackCars, false))
                .thenReturn("## Summary\nNo exact match, but here's the closest option.");

        ChatResponse response = orchestrator.handleMessage("conv-1", "Safety is my top priority");

        assertThat(response.completed()).isTrue();
        assertThat(response.assistantMessage()).isEqualTo("## Summary\nNo exact match, but here's the closest option.");
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).brand()).isEqualTo("Hyundai");
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        verify(conversationStore).save(conversation);
    }

    @Test
    void completesWithEmptyResultsWhenNoCarsMatchEvenAfterFallback() {
        when(preferenceAgent.extract(conversation)).thenReturn(completePreferences);
        when(sqlAgent.generateValidatedSql("conv-1", completePreferences))
                .thenReturn("SELECT * FROM cars WHERE price <= 100 LIMIT 5");
        when(databaseTool.execute("SELECT * FROM cars WHERE price <= 100 LIMIT 5")).thenReturn(List.of());
        when(sqlAgent.generateFallbackSql("conv-1", completePreferences))
                .thenReturn("SELECT * FROM cars LIMIT 5");
        when(databaseTool.execute("SELECT * FROM cars LIMIT 5")).thenReturn(List.of());

        ChatResponse response = orchestrator.handleMessage("conv-1", "Safety is my top priority");

        assertThat(response.completed()).isTrue();
        assertThat(response.recommendations()).isEmpty();
        assertThat(response.comparison()).isEmpty();
        assertThat(response.assistantMessage()).containsIgnoringCase("couldn't find");
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        verify(recommendationAgent, never()).explain(any(), any(), any(), anyBoolean());
        verify(conversationStore).save(conversation);
    }
}
