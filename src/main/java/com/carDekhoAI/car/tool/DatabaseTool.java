package com.carDekhoAI.car.tool;

import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.repository.CarRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DatabaseTool {

    private final JdbcTemplate jdbcTemplate;
    private final CarRepository carRepository;

    public DatabaseTool(JdbcTemplate jdbcTemplate, CarRepository carRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.carRepository = carRepository;
    }

    public List<Car> execute(String validatedSql) {
        List<Long> orderedIds;
        try {
            orderedIds = jdbcTemplate.query(validatedSql, (rs, rowNum) -> rs.getLong("id"));
        } catch (DataAccessException e) {
            throw new DatabaseQueryException("Failed to execute generated SQL query", e);
        }

        if (orderedIds.isEmpty()) {
            return List.of();
        }

        List<Car> fetched;
        try {
            fetched = carRepository.findAllById(orderedIds);
        } catch (DataAccessException e) {
            throw new DatabaseQueryException("Failed to load car details for query results", e);
        }

        Map<Long, Car> byId = new LinkedHashMap<>();
        for (Car car : fetched) {
            byId.put(car.getId(), car);
        }

        // findAllById does not preserve input order; re-derive the SQL's ORDER BY/LIMIT
        // ranking from orderedIds. filter(nonNull) guards the near-impossible case where
        // a returned id has no matching Car (e.g. deleted between the two queries).
        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
