package com.carDekhoAI.car.dto;

import com.carDekhoAI.car.entity.BodyType;
import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.entity.FuelType;
import com.carDekhoAI.car.entity.Transmission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarMapperTest {

    @Test
    void mapsAllFieldsFromEntityToResponse() {
        Car car = Car.builder()
                .id(1L)
                .brand("Maruti Suzuki")
                .model("Swift")
                .variant("ZXi+ AMT")
                .bodyType(BodyType.HATCHBACK)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .price(850000L)
                .engine("1197cc, 4-cylinder")
                .power("82 bhp @ 6000rpm")
                .torque("113 Nm @ 4200rpm")
                .mileage(22.4)
                .safetyRating(4)
                .bootSpace(268)
                .groundClearance(163)
                .seatCapacity(5)
                .reviewScore(4.3)
                .pros(List.of("Fun to drive", "Great mileage"))
                .cons(List.of("Firm ride quality"))
                .build();

        CarResponse response = CarMapper.toResponse(car);

        assertThat(response.id()).isEqualTo(car.getId());
        assertThat(response.brand()).isEqualTo(car.getBrand());
        assertThat(response.model()).isEqualTo(car.getModel());
        assertThat(response.variant()).isEqualTo(car.getVariant());
        assertThat(response.bodyType()).isEqualTo(car.getBodyType());
        assertThat(response.fuelType()).isEqualTo(car.getFuelType());
        assertThat(response.transmission()).isEqualTo(car.getTransmission());
        assertThat(response.price()).isEqualTo(car.getPrice());
        assertThat(response.engine()).isEqualTo(car.getEngine());
        assertThat(response.power()).isEqualTo(car.getPower());
        assertThat(response.torque()).isEqualTo(car.getTorque());
        assertThat(response.mileage()).isEqualTo(car.getMileage());
        assertThat(response.safetyRating()).isEqualTo(car.getSafetyRating());
        assertThat(response.bootSpace()).isEqualTo(car.getBootSpace());
        assertThat(response.groundClearance()).isEqualTo(car.getGroundClearance());
        assertThat(response.seatCapacity()).isEqualTo(car.getSeatCapacity());
        assertThat(response.reviewScore()).isEqualTo(car.getReviewScore());
        assertThat(response.pros()).isEqualTo(car.getPros());
        assertThat(response.cons()).isEqualTo(car.getCons());
    }
}
