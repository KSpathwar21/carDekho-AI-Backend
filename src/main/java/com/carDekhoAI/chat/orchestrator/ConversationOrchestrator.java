package com.carDekhoAI.chat.orchestrator;

import com.carDekhoAI.car.dto.CarMapper;
import com.carDekhoAI.car.dto.CarResponse;
import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.tool.DatabaseTool;
import com.carDekhoAI.chat.agent.ConversationAgent;
import com.carDekhoAI.chat.dto.ChatResponse;
import com.carDekhoAI.chat.model.Conversation;
import com.carDekhoAI.chat.model.ConversationStatus;
import com.carDekhoAI.chat.model.Message;
import com.carDekhoAI.chat.model.MessageRole;
import com.carDekhoAI.chat.store.ConversationStore;
import com.carDekhoAI.preference.agent.PreferenceAgent;
import com.carDekhoAI.preference.dto.UserPreference;
import com.carDekhoAI.recommendation.agent.RecommendationAgent;
import com.carDekhoAI.sql.agent.SqlAgent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ConversationOrchestrator {

    private final ConversationStore conversationStore;
    private final ConversationAgent conversationAgent;
    private final PreferenceAgent preferenceAgent;
    private final SqlAgent sqlAgent;
    private final DatabaseTool databaseTool;
    private final RecommendationAgent recommendationAgent;

    public ConversationOrchestrator(ConversationStore conversationStore,
                                     ConversationAgent conversationAgent,
                                     PreferenceAgent preferenceAgent,
                                     SqlAgent sqlAgent,
                                     DatabaseTool databaseTool,
                                     RecommendationAgent recommendationAgent) {
        this.conversationStore = conversationStore;
        this.conversationAgent = conversationAgent;
        this.preferenceAgent = preferenceAgent;
        this.sqlAgent = sqlAgent;
        this.databaseTool = databaseTool;
        this.recommendationAgent = recommendationAgent;
    }

    public ChatResponse handleMessage(String conversationId, String userMessage) {
        Conversation conversation = conversationStore.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        conversation.getMessages().add(new Message(MessageRole.USER, userMessage, LocalDateTime.now()));

        UserPreference preferences = preferenceAgent.extract(conversation);
        conversation.setPreferences(preferences);

        ChatResponse response;
        if (preferences.isComplete()) {
            conversation.setStatus(ConversationStatus.COMPLETED);
            response = buildRecommendationResponse(conversationId, preferences);
        } else {
            String question = conversationAgent.nextQuestion(conversation, preferences);
            response = new ChatResponse(question, List.of(), List.of(), false);
        }

        conversation.getMessages().add(
                new Message(MessageRole.ASSISTANT, response.assistantMessage(), LocalDateTime.now()));
        conversationStore.save(conversation);

        return response;
    }

    private ChatResponse buildRecommendationResponse(String conversationId, UserPreference preferences) {
        String sql = sqlAgent.generateValidatedSql(conversationId, preferences);
        List<Car> cars = databaseTool.execute(sql);
        boolean exactMatch = true;

        if (cars.isEmpty()) {
            String fallbackSql = sqlAgent.generateFallbackSql(conversationId, preferences);
            cars = databaseTool.execute(fallbackSql);
            exactMatch = false;
        }

        if (cars.isEmpty()) {
            String message = "I couldn't find any cars matching all of your criteria. "
                    + "Try relaxing your budget or one of your other preferences and I'll take another look.";
            return new ChatResponse(message, List.of(), List.of(), true);
        }

        String assistantMessage = recommendationAgent.explain(conversationId, preferences, cars, exactMatch);
        List<CarResponse> carResponses = cars.stream().map(CarMapper::toResponse).toList();
        return new ChatResponse(assistantMessage, carResponses, carResponses, true);
    }
}
