package com.carDekhoAI.recommendation.agent;

import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendationAgent {

    private static final String SYSTEM_PROMPT = """
            You are an automotive expert helping a customer choose a car in India.

            You will be given the customer's stated preferences and a ranked list of
            cars that matched those preferences in the database, in ranked order
            (most recommended first).

            Write a markdown-formatted response that:
            - Opens with a brief summary of what the customer is looking for and
              how well these cars match.
            - For each car, explains why it suits the customer, referencing its
              Pros and Cons (as provided) and any relevant trade-offs versus the
              customer's stated priority.
            - Closes with a short "Alternative Suggestions" section noting what
              the customer could consider adjusting (e.g. a different body type
              or a slightly higher budget) if none of these are a perfect fit.

            CRITICAL: Only reference cars, specs, prices, pros, and cons that are
            explicitly provided below. Never invent or assume details that are
            not given. Never hallucinate a car, feature, or price that is not in
            the provided data.

            Use markdown formatting (headings, bold, bullet lists) throughout.
            Return ONLY the markdown response. No preamble, no code fences.""";

    private final LlmClient llmClient;

    public RecommendationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String explain(String conversationId, UserPreference preferences, List<Car> cars) {
        String userMessage = buildUserMessage(preferences, cars);
        return llmClient.call(conversationId, SYSTEM_PROMPT, userMessage);
    }

    private String buildUserMessage(UserPreference preferences, List<Car> cars) {
        StringBuilder message = new StringBuilder("Customer preferences:\n");
        appendIfPresent(message, "budget", preferences.budget());
        appendIfPresent(message, "fuelType", preferences.fuelType());
        appendIfPresent(message, "bodyType", preferences.bodyType());
        appendIfPresent(message, "transmission", preferences.transmission());
        appendIfPresent(message, "drivingPattern", preferences.drivingPattern());
        appendIfPresent(message, "familySize", preferences.familySize());
        appendIfPresent(message, "priority", preferences.priority());
        appendIfPresent(message, "brandPreference", preferences.brandPreference());
        appendIfPresent(message, "groundClearance", preferences.groundClearance());
        appendIfPresent(message, "bootSpace", preferences.bootSpace());

        message.append("\nMatched cars (ranked, most recommended first):\n");
        for (int i = 0; i < cars.size(); i++) {
            message.append('\n').append(i + 1).append(". ").append(renderCar(cars.get(i))).append('\n');
        }
        return message.toString();
    }

    private void appendIfPresent(StringBuilder message, String label, Object value) {
        if (value != null) {
            message.append(label).append(": ").append(value).append('\n');
        }
    }

    private String renderCar(Car car) {
        StringBuilder block = new StringBuilder();
        block.append(car.getBrand()).append(' ').append(car.getModel())
                .append(' ').append(car.getVariant()).append('\n');
        block.append("   price: ").append(car.getPrice()).append('\n');
        block.append("   mileage: ").append(car.getMileage()).append('\n');
        block.append("   safetyRating: ").append(car.getSafetyRating()).append('\n');
        block.append("   reviewScore: ").append(car.getReviewScore()).append('\n');
        block.append("   pros: ").append(String.join("; ", car.getPros())).append('\n');
        block.append("   cons: ").append(String.join("; ", car.getCons()));
        return block.toString();
    }
}
