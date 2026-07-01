package com.carDekhoAI.car.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(nullable = false, length = 80)
    private String variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, length = 20)
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Transmission transmission;

    @Column(nullable = false)
    private Long price;

    @Column(length = 100)
    private String engine;

    @Column(length = 50)
    private String power;

    @Column(length = 50)
    private String torque;

    private Double mileage;

    @Column(name = "safety_rating")
    private Integer safetyRating;

    @Column(name = "boot_space")
    private Integer bootSpace;

    @Column(name = "ground_clearance")
    private Integer groundClearance;

    @Column(name = "seat_capacity")
    private Integer seatCapacity;

    @Column(name = "review_score")
    private Double reviewScore;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "car_pros", joinColumns = @JoinColumn(name = "car_id"))
    @Column(name = "pro")
    private List<String> pros = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "car_cons", joinColumns = @JoinColumn(name = "car_id"))
    @Column(name = "con")
    private List<String> cons = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
