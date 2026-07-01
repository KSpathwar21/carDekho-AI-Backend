package com.carDekhoAI.car.dto;

import com.carDekhoAI.car.entity.Car;

public final class CarMapper {

    private CarMapper() {
    }

    public static CarResponse toResponse(Car car) {
        return new CarResponse(
                car.getId(),
                car.getBrand(),
                car.getModel(),
                car.getVariant(),
                car.getBodyType(),
                car.getFuelType(),
                car.getTransmission(),
                car.getPrice(),
                car.getEngine(),
                car.getPower(),
                car.getTorque(),
                car.getMileage(),
                car.getSafetyRating(),
                car.getBootSpace(),
                car.getGroundClearance(),
                car.getSeatCapacity(),
                car.getReviewScore(),
                car.getPros(),
                car.getCons()
        );
    }
}
