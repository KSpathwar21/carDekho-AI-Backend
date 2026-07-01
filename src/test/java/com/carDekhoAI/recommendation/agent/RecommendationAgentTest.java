package com.carDekhoAI.recommendation.agent;

import com.carDekhoAI.car.entity.BodyType;
import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.entity.FuelType;
import com.carDekhoAI.car.entity.Transmission;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationAgentTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final RecommendationAgent recommendationAgent = new RecommendationAgent(llmClient);

    private final UserPreference preferences = new UserPreference(
            1500000L, "Petrol", "SUV", "Automatic", "City", 4, "Safety", null, null, null);

    private Car carA() {
        return Car.builder()
                .id(1L)
                .brand("Maruti Suzuki")
                .model("Baleno")
                .variant("Alpha")
                .bodyType(BodyType.HATCHBACK)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .price(800000L)
                .mileage(22.9)
                .safetyRating(3)
                .reviewScore(4.2)
                .pros(List.of("Great mileage"))
                .cons(List.of("Small boot"))
                .build();
    }

    private Car carB() {
        return Car.builder()
                .id(2L)
                .brand("Tata")
                .model("Nexon")
                .variant("Fearless")
                .bodyType(BodyType.SUV)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .price(1650000L)
                .mileage(17.4)
                .safetyRating(5)
                .reviewScore(4.5)
                .pros(List.of("5-star safety"))
                .cons(List.of("Firm ride"))
                .build();
    }

    @Test
    void returnsLlmMarkdownPassthrough() {
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn("## Summary\nGreat picks!");

        String result = recommendationAgent.explain("conv-1", preferences, List.of(carA()));

        assertThat(result).isEqualTo("## Summary\nGreat picks!");
    }

    @Test
    void includesCarDataInUserPrompt() {
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), anyString(), promptCaptor.capture())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA()));

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("Maruti Suzuki", "Baleno", "800000", "Great mileage", "Small boot");
    }

    @Test
    void includesPreferenceDataInUserPrompt() {
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), anyString(), promptCaptor.capture())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA()));

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("budget: 1500000", "priority: Safety");
    }

    @Test
    void preservesCarRankOrderInPrompt() {
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), anyString(), promptCaptor.capture())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA(), carB()));

        String prompt = promptCaptor.getValue();
        assertThat(prompt.indexOf("Baleno")).isLessThan(prompt.indexOf("Nexon"));
    }

    @Test
    void threadsConversationIdToLlmClient() {
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA()));

        verify(llmClient).call(eq("conv-1"), anyString(), anyString());
    }

    @Test
    void threeArgOverloadDefaultsToExactMatch() {
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), anyString(), promptCaptor.capture())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA()));

        assertThat(promptCaptor.getValue()).containsIgnoringCase("Match type: EXACT");
    }

    @Test
    void marksPromptAsClosestMatchWhenExactMatchIsFalse() {
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), anyString(), promptCaptor.capture())).thenReturn("ok");

        recommendationAgent.explain("conv-1", preferences, List.of(carA()), false);

        assertThat(promptCaptor.getValue()).containsIgnoringCase("Match type: CLOSEST");
    }

    @Test
    void handlesEmptyProsAndConsWithoutError() {
        Car noProsAndCons = Car.builder()
                .id(3L)
                .brand("Test")
                .model("Model")
                .variant("Variant")
                .bodyType(BodyType.SEDAN)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.MANUAL)
                .price(500000L)
                .build();
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn("ok");

        String result = recommendationAgent.explain("conv-1", preferences, List.of(noProsAndCons));

        assertThat(result).isEqualTo("ok");
    }
}
