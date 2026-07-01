package com.carDekhoAI.car.repository;

import com.carDekhoAI.car.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarRepository extends JpaRepository<Car, Long> {
}
