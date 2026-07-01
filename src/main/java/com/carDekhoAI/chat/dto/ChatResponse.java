package com.carDekhoAI.chat.dto;

import com.carDekhoAI.car.dto.CarResponse;

import java.util.List;

public record ChatResponse(
        String assistantMessage,
        List<CarResponse> recommendations,
        List<CarResponse> comparison,
        boolean completed
) {
}
