package com.carDekhoAI.sql.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    // --- forbidden statement types -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE cars SET price = 0",
            "DELETE FROM cars",
            "INSERT INTO cars (brand) VALUES ('Hack')",
            "DROP TABLE cars",
            "ALTER TABLE cars ADD COLUMN hacked INT",
            "CREATE TABLE evil (id INT)",
            "TRUNCATE TABLE cars",
            "SELECT * FROM cars UNION SELECT * FROM cars"
    })
    void rejectsForbiddenStatementTypes(String sql) {
        SqlValidationResult result = validator.validate(sql);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void rejectsForbiddenKeywordCaseInsensitively() {
        SqlValidationResult result = validator.validate("update cars set price = 0");

        assertThat(result.valid()).isFalse();
    }

    // --- false-positive regression guards -----------------------------------------------

    @Test
    void doesNotRejectUpdatedAtColumn() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars ORDER BY updated_at DESC LIMIT 5");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void doesNotRejectCreatedAtColumn() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars WHERE created_at IS NOT NULL LIMIT 5");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void doesNotRejectSafetyRatingColumn() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars ORDER BY safety_rating DESC LIMIT 5");

        assertThat(result.valid()).isTrue();
    }

    // --- structural checks -----------------------------------------------------------

    @Test
    void rejectsMultipleStatementsViaSemicolon() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars; DROP TABLE cars");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void allowsSingleTrailingSemicolon() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars LIMIT 5;");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsLineCommentObfuscation() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars -- WHERE price < 100\nAND 1=1; DROP TABLE cars");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("comment");
    }

    @Test
    void rejectsBlockCommentObfuscation() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars /* sneaky */ WHERE price <= 100");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("comment");
    }

    @Test
    void rejectsHashCommentObfuscation() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars # comment\nLIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("comment");
    }

    @Test
    void rejectsSubqueryInWhereClause() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars WHERE price = (SELECT MAX(price) FROM cars)");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("subquer");
    }

    @Test
    void rejectsSubqueryInSelectItem() {
        SqlValidationResult result = validator.validate(
                "SELECT (SELECT COUNT(*) FROM cars), brand FROM cars LIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("subquer");
    }

    @Test
    void rejectsSubqueryInHavingClause() {
        SqlValidationResult result = validator.validate(
                "SELECT brand, COUNT(*) FROM cars GROUP BY brand "
                        + "HAVING COUNT(*) > (SELECT AVG(price) FROM cars)");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("subquer");
    }

    @Test
    void rejectsUnparseableSyntax() {
        SqlValidationResult result = validator.validate("SELEKT * FRM cars WHERE");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("could not be parsed");
    }

    @Test
    void rejectsWrongTable() {
        SqlValidationResult result = validator.validate("SELECT * FROM users LIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("cars");
    }

    @Test
    void rejectsJoin() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars JOIN car_pros ON cars.id = car_pros.car_id");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("JOIN");
    }

    @Test
    void rejectsUnknownColumnInOrderBy() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars ORDER BY CASE WHEN priority = 'budget' THEN price ELSE review_score END LIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("priority");
    }

    @Test
    void rejectsUnknownColumnInWhereClause() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars WHERE drivingPattern = 'City' LIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("drivingPattern");
    }

    @Test
    void rejectsUnknownColumnInSelectList() {
        SqlValidationResult result = validator.validate("SELECT id, priority FROM cars LIMIT 5");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("priority");
    }

    @Test
    void rejectsCommonTableExpressionBypassAttempt() {
        SqlValidationResult result = validator.validate("WITH x AS (SELECT 1) SELECT * FROM x");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void rejectsEmptySql() {
        assertThat(validator.validate("").valid()).isFalse();
        assertThat(validator.validate(null).valid()).isFalse();
        assertThat(validator.validate("   ").valid()).isFalse();
    }

    // --- happy path --------------------------------------------------------------------

    @Test
    void acceptsSimpleValidQueryFromDocsExample() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars WHERE price <= 1500000 AND fuel_type='PETROL' ORDER BY safety_rating DESC LIMIT 5");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsQueryWithLikeAndOr() {
        SqlValidationResult result = validator.validate(
                "SELECT * FROM cars WHERE brand LIKE '%Maruti%' OR brand LIKE '%Hyundai%' ORDER BY price ASC LIMIT 5");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsQueryWithNoWhereClause() {
        SqlValidationResult result = validator.validate("SELECT * FROM cars ORDER BY review_score DESC LIMIT 10");

        assertThat(result.valid()).isTrue();
    }
}
