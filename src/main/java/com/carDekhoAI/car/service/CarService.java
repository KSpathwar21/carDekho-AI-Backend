package com.carDekhoAI.car.service;

import com.carDekhoAI.car.dto.CarMapper;
import com.carDekhoAI.car.dto.CarResponse;
import com.carDekhoAI.car.repository.CarRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CarService {

    private final CarRepository carRepository;

    public CarService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public Page<CarResponse> getCars(Pageable pageable) {
        return carRepository.findAll(pageable).map(CarMapper::toResponse);
    }

    public CarResponse getCarById(Long id) {
        return carRepository.findById(id)
                .map(CarMapper::toResponse)
                .orElseThrow(() -> new CarNotFoundException(id));
    }
}
