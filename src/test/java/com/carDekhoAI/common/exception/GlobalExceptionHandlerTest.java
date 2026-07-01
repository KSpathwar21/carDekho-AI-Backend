package com.carDekhoAI.common.exception;

import com.carDekhoAI.car.service.CarNotFoundException;
import com.carDekhoAI.car.tool.DatabaseQueryException;
import com.carDekhoAI.chat.orchestrator.ConversationNotFoundException;
import com.carDekhoAI.llm.client.LlmException;
import com.carDekhoAI.preference.agent.PreferenceExtractionException;
import com.carDekhoAI.sql.agent.SqlGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/test/path");
    }

    @Test
    void handlesCarNotFoundAs404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new CarNotFoundException(5L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).contains("5");
        assertThat(response.getBody().path()).isEqualTo("/test/path");
    }

    @Test
    void handlesConversationNotFoundAs404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ConversationNotFoundException("conv-1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("conv-1");
    }

    @Test
    void handlesValidationErrorsAs400WithJoinedFieldMessages() {
        BindingResult bindingResult = mock(BindingResult.class);
        List<FieldError> fieldErrors = List.of(
                new FieldError("chatRequest", "conversationId", "must not be blank"),
                new FieldError("chatRequest", "message", "must not be blank")
        );
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Validation Failed");
        assertThat(response.getBody().message())
                .contains("conversationId: must not be blank")
                .contains("message: must not be blank");
    }

    @Test
    void handlesSqlGenerationExceptionAs500WithInvalidSqlLabel() {
        ResponseEntity<ErrorResponse> response =
                handler.handleSqlGeneration(new SqlGenerationException("could not generate valid SQL"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("Invalid SQL");
        assertThat(response.getBody().message()).isEqualTo("could not generate valid SQL");
    }

    @Test
    void handlesLlmExceptionAs503WithLlmFailureLabel() {
        ResponseEntity<ErrorResponse> response = handler.handleUpstreamDependencyFailure(
                new LlmException("call failed", new RuntimeException("boom")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error()).isEqualTo("LLM Failure");
    }

    @Test
    void handlesPreferenceExtractionExceptionAs503WithLlmFailureLabel() {
        ResponseEntity<ErrorResponse> response = handler.handleUpstreamDependencyFailure(
                new PreferenceExtractionException("bad json", new RuntimeException("boom")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error()).isEqualTo("LLM Failure");
    }

    @Test
    void handlesDatabaseQueryExceptionAs503WithDatabaseFailureLabel() {
        ResponseEntity<ErrorResponse> response = handler.handleUpstreamDependencyFailure(
                new DatabaseQueryException("query failed", new RuntimeException("boom")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error()).isEqualTo("Database Failure");
    }

    @Test
    void handlesGenericExceptionAs500WithoutLeakingOriginalMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("sensitive internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).doesNotContain("sensitive internal detail");
    }
}
