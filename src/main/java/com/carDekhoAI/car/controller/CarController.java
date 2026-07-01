package com.carDekhoAI.car.controller;

import com.carDekhoAI.car.dto.CarResponse;
import com.carDekhoAI.car.service.CarService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping
    public Page<CarResponse> getCars(Pageable pageable) {
        return carService.getCars(pageable);
    }

    @GetMapping("/{id}")
    public CarResponse getCarById(@PathVariable Long id) {
        return carService.getCarById(id);
    }
}
