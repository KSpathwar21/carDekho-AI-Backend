package com.carDekhoAI.car.tool;

import com.carDekhoAI.car.entity.BodyType;
import com.carDekhoAI.car.entity.Car;
import com.carDekhoAI.car.entity.FuelType;
import com.carDekhoAI.car.entity.Transmission;
import com.carDekhoAI.car.repository.CarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseToolTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CarRepository carRepository = mock(CarRepository.class);
    private final DatabaseTool databaseTool = new DatabaseTool(jdbcTemplate, carRepository);

    private Car car(long id, String brand) {
        return Car.builder()
                .id(id)
                .brand(brand)
                .model("Model")
                .variant("Variant")
                .bodyType(BodyType.SUV)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .price(1000000L)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void stubOrderedIds(List<Long> ids) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(ids);
    }

    @Test
    void reordersResultsToMatchQueryOrder() {
        stubOrderedIds(List.of(3L, 1L, 2L));
        Car car1 = car(1L, "One");
        Car car2 = car(2L, "Two");
        Car car3 = car(3L, "Three");
        when(carRepository.findAllById(List.of(3L, 1L, 2L))).thenReturn(List.of(car1, car2, car3));

        List<Car> result = databaseTool.execute("SELECT * FROM cars");

        assertThat(result).containsExactly(car3, car1, car2);
    }

    @Test
    void returnsEmptyListWhenNoIdsMatch() {
        stubOrderedIds(List.of());

        List<Car> result = databaseTool.execute("SELECT * FROM cars WHERE price < 0");

        assertThat(result).isEmpty();
        verify(carRepository, never()).findAllById(any());
    }

    @Test
    void wrapsDataAccessExceptionFromIdQuery() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("bad", "SELECT", new java.sql.SQLException()));

        assertThatThrownBy(() -> databaseTool.execute("SELECT bogus FROM cars"))
                .isInstanceOf(DatabaseQueryException.class)
                .hasCauseInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void wrapsDataAccessExceptionFromRepositoryFetch() {
        stubOrderedIds(List.of(1L));
        when(carRepository.findAllById(List.of(1L)))
                .thenThrow(new BadSqlGrammarException("bad", "SELECT", new java.sql.SQLException()));

        assertThatThrownBy(() -> databaseTool.execute("SELECT * FROM cars"))
                .isInstanceOf(DatabaseQueryException.class)
                .hasCauseInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void dropsUnresolvedIdsWithoutThrowing() {
        stubOrderedIds(List.of(1L, 2L, 3L));
        Car car1 = car(1L, "One");
        Car car3 = car(3L, "Three");
        when(carRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(car1, car3));

        List<Car> result = databaseTool.execute("SELECT * FROM cars");

        assertThat(result).containsExactly(car1, car3);
    }

    @Test
    void passesSqlThroughUnmodifiedToJdbcTemplate() {
        String sql = "SELECT * FROM cars WHERE price <= 1500000 LIMIT 5";
        stubOrderedIds(List.of());

        databaseTool.execute(sql);

        verify(jdbcTemplate).query(eq(sql), any(RowMapper.class));
    }
}
