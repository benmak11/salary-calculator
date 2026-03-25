package app.salary.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationExceptions_withFieldErrors_shouldReturnBadRequest() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError = new FieldError("request", "country", "Country is required");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(fieldError));
        when(bindingResult.getGlobalErrors()).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Country is required", response.getBody().get("country"));
    }

    @Test
    void handleValidationExceptions_withGlobalErrors_shouldReturnBadRequest() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        ObjectError globalError = new ObjectError("request", "Invalid country options");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());
        when(bindingResult.getGlobalErrors()).thenReturn(Collections.singletonList(globalError));

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid country options", response.getBody().get("request"));
    }

    @Test
    void handleValidationExceptions_withMultipleErrors_shouldReturnAllErrors() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError error1 = new FieldError("request", "country", "Country is required");
        FieldError error2 = new FieldError("request", "taxYear", "Tax year must be valid");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Arrays.asList(error1, error2));
        when(bindingResult.getGlobalErrors()).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void handleIllegalArgumentException_shouldReturn422() {
        IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgumentException(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid parameter", response.getBody().get("error"));
    }

    @Test
    void handleGenericException_shouldReturn500() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<Map<String, String>> response = handler.handleGenericException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().get("error"));
    }
}
