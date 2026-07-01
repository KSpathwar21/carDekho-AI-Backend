package com.carDekhoAI.car.dto;

import com.carDekhoAI.car.entity.BodyType;
import com.carDekhoAI.car.entity.FuelType;
import com.carDekhoAI.car.entity.Transmission;

import java.util.List;

public record CarResponse(
        Long id,
        String brand,
        String model,
        String variant,
        BodyType bodyType,
        FuelType fuelType,
        Transmission transmission,
        Long price,
        String engine,
        String power,
        String torque,
        Double mileage,
        Integer safetyRating,
        Integer bootSpace,
        Integer groundClearance,
        Integer seatCapacity,
        Double reviewScore,
        List<String> pros,
        List<String> cons
) {
}
