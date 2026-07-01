package com.carDekhoAI.sql.agent;

import com.carDekhoAI.llm.client.LlmClient;
import com.carDekhoAI.preference.dto.UserPreference;
import com.carDekhoAI.sql.validator.SqlValidationResult;
import com.carDekhoAI.sql.validator.SqlValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlAgentTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final SqlValidator sqlValidator = mock(SqlValidator.class);
    private final SqlAgent sqlAgent = new SqlAgent(llmClient, sqlValidator);

    private final UserPreference preferences = new UserPreference(
            1500000L, "Petrol", "SUV", "Automatic", "City", 4, "Safety", null, null, null);

    @Test
    void returnsValidatedSqlOnFirstAttempt() {
        String validSql = "SELECT * FROM cars WHERE fuel_type='PETROL' LIMIT 5";
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn(validSql);
        when(sqlValidator.validate(validSql)).thenReturn(SqlValidationResult.ok());

        String result = sqlAgent.generateValidatedSql("conv-1", preferences);

        assertThat(result).isEqualTo(validSql);
        verify(llmClient, times(1)).call(eq("conv-1"), anyString(), anyString());
    }

    @Test
    void stripsMarkdownFencesBeforeValidating() {
        when(llmClient.call(eq("conv-1"), anyString(), anyString()))
                .thenReturn("```sql\nSELECT * FROM cars LIMIT 5\n```");
        when(sqlValidator.validate("SELECT * FROM cars LIMIT 5")).thenReturn(SqlValidationResult.ok());

        String result = sqlAgent.generateValidatedSql("conv-1", preferences);

        assertThat(result).isEqualTo("SELECT * FROM cars LIMIT 5");
    }

    @Test
    void regeneratesWithFeedbackAfterFirstRejection() {
        String invalidSql = "SELECT * FROM users LIMIT 5";
        String validSql = "SELECT * FROM cars LIMIT 5";
        when(llmClient.call(eq("conv-1"), anyString(), anyString()))
                .thenReturn(invalidSql)
                .thenReturn(validSql);
        when(sqlValidator.validate(invalidSql))
                .thenReturn(SqlValidationResult.reject("SQL must only query the 'cars' table"));
        when(sqlValidator.validate(validSql)).thenReturn(SqlValidationResult.ok());

        String result = sqlAgent.generateValidatedSql("conv-1", preferences);

        assertThat(result).isEqualTo(validSql);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(2)).call(eq("conv-1"), anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1)).contains("SQL must only query the 'cars' table");
    }

    @Test
    void throwsSqlGenerationExceptionAfterExhaustingAttempts() {
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn("SELECT * FROM users LIMIT 5");
        when(sqlValidator.validate(anyString())).thenReturn(SqlValidationResult.reject("always invalid"));

        assertThatThrownBy(() -> sqlAgent.generateValidatedSql("conv-1", preferences))
                .isInstanceOf(SqlGenerationException.class)
                .hasMessageContaining("always invalid");
        verify(llmClient, times(3)).call(eq("conv-1"), anyString(), anyString());
    }

    @Test
    void generateFallbackSqlReturnsValidatedSqlOnFirstAttempt() {
        String fallbackSql = "SELECT * FROM cars ORDER BY ABS(price - 1500000) LIMIT 5";
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn(fallbackSql);
        when(sqlValidator.validate(fallbackSql)).thenReturn(SqlValidationResult.ok());

        String result = sqlAgent.generateFallbackSql("conv-1", preferences);

        assertThat(result).isEqualTo(fallbackSql);
    }

    @Test
    void generateFallbackSqlUsesADifferentSystemPromptThanStrictGeneration() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.call(eq("conv-1"), systemPromptCaptor.capture(), anyString()))
                .thenReturn("SELECT * FROM cars LIMIT 5");
        when(sqlValidator.validate(anyString())).thenReturn(SqlValidationResult.ok());

        sqlAgent.generateValidatedSql("conv-1", preferences);
        sqlAgent.generateFallbackSql("conv-1", preferences);

        assertThat(systemPromptCaptor.getAllValues()).hasSize(2);
        assertThat(systemPromptCaptor.getAllValues().get(0)).isNotEqualTo(systemPromptCaptor.getAllValues().get(1));
        assertThat(systemPromptCaptor.getAllValues().get(1)).containsIgnoringCase("closest");
    }

    @Test
    void generateFallbackSqlThrowsSqlGenerationExceptionAfterExhaustingAttempts() {
        when(llmClient.call(eq("conv-1"), anyString(), anyString())).thenReturn("SELECT * FROM users LIMIT 5");
        when(sqlValidator.validate(anyString())).thenReturn(SqlValidationResult.reject("always invalid"));

        assertThatThrownBy(() -> sqlAgent.generateFallbackSql("conv-1", preferences))
                .isInstanceOf(SqlGenerationException.class)
                .hasMessageContaining("always invalid");
        verify(llmClient, times(3)).call(eq("conv-1"), anyString(), anyString());
    }
}
