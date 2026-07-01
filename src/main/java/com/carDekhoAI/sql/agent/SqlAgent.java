package com.carDekhoAI.sql.agent;

import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import com.carDekhoAI.sql.validator.SqlValidationResult;
import com.carDekhoAI.sql.validator.SqlValidator;
import org.springframework.stereotype.Component;

@Component
public class SqlAgent {

    private static final int MAX_ATTEMPTS = 3;

    private static final String SYSTEM_PROMPT = """
            Generate a single SQL SELECT query for MySQL to find cars matching the user's preferences.

            Table: cars
            Columns:
              id (bigint), brand (varchar), model (varchar), variant (varchar),
              body_type (varchar - one of: HATCHBACK, SEDAN, SUV, MUV, COUPE),
              fuel_type (varchar - one of: PETROL, DIESEL, CNG, ELECTRIC, HYBRID),
              transmission (varchar - one of: MANUAL, AUTOMATIC),
              price (bigint, in INR), engine (varchar), power (varchar), torque (varchar),
              mileage (double), safety_rating (int, 1-5), boot_space (int, litres),
              ground_clearance (int, mm), seat_capacity (int), review_score (double, 0-5),
              created_at, updated_at.

            CRITICAL: body_type, fuel_type, and transmission are stored as the EXACT
            UPPERCASE enum values shown above (e.g. 'PETROL', not 'Petrol' or 'gasoline').
            Always uppercase these values in WHERE clauses so they match stored data exactly.

            Only query the cars table. Do not use JOIN, subqueries, or any other table.

            Two preference fields have no direct column:
              - drivingPattern (e.g. "City", "Highway", "Off-road"): use this to influence
                ORDER BY choice, not as a WHERE filter. City driving favors higher mileage
                (ORDER BY mileage DESC); highway favors safety_rating or power; off-road
                favors ground_clearance.
              - priority (e.g. "Safety", "Mileage", "Budget", "Space"): use this to choose
                the primary ORDER BY column (safety_rating, mileage, price, boot_space
                respectively), breaking ties with review_score DESC.
            Never reference drivingPattern or priority as column names - they do not exist.

            Rules:
            - Generate SQL for MySQL only.
            - Only SELECT statements. Never UPDATE, DELETE, INSERT, DROP, ALTER, CREATE,
              TRUNCATE, or UNION.
            - Never use subqueries or multiple statements (no semicolons except one
              optional trailing semicolon).
            - Never use SQL comments.
            - Always include a LIMIT clause (default 5 if not otherwise implied).
            - Return ONLY the raw SQL query. No explanation, no markdown, no code fences.""";

    private static final String FALLBACK_SYSTEM_PROMPT = """
            Generate a single SQL SELECT query for MySQL to find the CLOSEST-MATCHING cars
            to the user's preferences. A strict query using these preferences as WHERE
            filters already returned zero rows, so this query must not exclude cars for
            failing to match a preference exactly - rank by closeness instead.

            Table: cars
            Columns:
              id (bigint), brand (varchar), model (varchar), variant (varchar),
              body_type (varchar - one of: HATCHBACK, SEDAN, SUV, MUV, COUPE),
              fuel_type (varchar - one of: PETROL, DIESEL, CNG, ELECTRIC, HYBRID),
              transmission (varchar - one of: MANUAL, AUTOMATIC),
              price (bigint, in INR), engine (varchar), power (varchar), torque (varchar),
              mileage (double), safety_rating (int, 1-5), boot_space (int, litres),
              ground_clearance (int, mm), seat_capacity (int), review_score (double, 0-5),
              created_at, updated_at.

            CRITICAL: body_type, fuel_type, and transmission are stored as the EXACT
            UPPERCASE enum values shown above (e.g. 'PETROL', not 'Petrol' or 'gasoline').

            Only query the cars table. Do not use JOIN, subqueries, or any other table.

            Rules:
            - Do NOT filter out cars with a WHERE clause that requires every preference to
              match (that already returned zero rows). Use no WHERE clause, or only a very
              loose one (e.g. price within a wide band of the budget) so the table is never
              filtered down to zero rows again.
            - Instead, use ORDER BY to rank cars by closeness to the stated preferences -
              e.g. combine terms like ABS(price - <budget>) ascending, and
              CASE WHEN body_type = '<bodyType>' THEN 0 ELSE 1 END, CASE WHEN
              fuel_type = '<fuelType>' THEN 0 ELSE 1 END, CASE WHEN
              transmission = '<transmission>' THEN 0 ELSE 1 END ascending, so exact
              matches on any given field naturally sort first without excluding others.
              Only reference preference fields that were actually provided.
            - Generate SQL for MySQL only.
            - Only SELECT statements. Never UPDATE, DELETE, INSERT, DROP, ALTER, CREATE,
              TRUNCATE, or UNION.
            - Never use subqueries or multiple statements (no semicolons except one
              optional trailing semicolon).
            - Never use SQL comments.
            - Always include a LIMIT clause (default 5 if not otherwise implied).
            - Return ONLY the raw SQL query. No explanation, no markdown, no code fences.""";

    private final LlmClient llmClient;
    private final SqlValidator sqlValidator;

    public SqlAgent(LlmClient llmClient, SqlValidator sqlValidator) {
        this.llmClient = llmClient;
        this.sqlValidator = sqlValidator;
    }

    public String generateValidatedSql(String conversationId, UserPreference preferences) {
        return generateValidatedSql(conversationId, SYSTEM_PROMPT, buildUserMessage(preferences));
    }

    /**
     * Used when {@link #generateValidatedSql(String, UserPreference)}'s strict query matched
     * zero cars - generates a query ranking by closeness to preferences instead of requiring
     * every field to match, so the orchestrator can offer near-matches instead of a dead end.
     */
    public String generateFallbackSql(String conversationId, UserPreference preferences) {
        return generateValidatedSql(conversationId, FALLBACK_SYSTEM_PROMPT, buildUserMessage(preferences));
    }

    private String generateValidatedSql(String conversationId, String systemPrompt, String userMessage) {
        String feedback = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = feedback == null
                    ? userMessage
                    : userMessage + "\n\nYour previous attempt was rejected: " + feedback
                            + "\nGenerate a corrected SQL query that fixes this specific issue.";

            String rawSql = llmClient.call(conversationId, systemPrompt, prompt);
            String sql = stripMarkdownFences(rawSql);

            SqlValidationResult result = sqlValidator.validate(sql);
            if (result.valid()) {
                return sql;
            }
            feedback = result.reason();
        }

        throw new SqlGenerationException(
                "Failed to generate valid SQL after " + MAX_ATTEMPTS + " attempts. Last rejection reason: "
                        + feedback);
    }

    private String buildUserMessage(UserPreference preferences) {
        StringBuilder message = new StringBuilder("User preferences:\n");
        appendIfPresent(message, "budget", preferences.budget());
        appendIfPresent(message, "fuelType", preferences.fuelType());
        appendIfPresent(message, "bodyType", preferences.bodyType());
        appendIfPresent(message, "transmission", preferences.transmission());
        appendIfPresent(message, "drivingPattern", preferences.drivingPattern());
        appendIfPresent(message, "familySize", preferences.familySize());
        appendIfPresent(message, "priority", preferences.priority());
        appendIfPresent(message, "brandPreference", preferences.brandPreference());
        appendIfPresent(message, "groundClearance", preferences.groundClearance());
        appendIfPresent(message, "bootSpace", preferences.bootSpace());
        return message.toString();
    }

    private void appendIfPresent(StringBuilder message, String label, Object value) {
        if (value != null) {
            message.append(label).append(": ").append(value).append('\n');
        }
    }

    private String stripMarkdownFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceFirst("```\\s*$", "");
        }
        return trimmed.trim();
    }
}
