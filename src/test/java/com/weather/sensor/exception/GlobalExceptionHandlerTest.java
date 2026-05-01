package com.weather.sensor.exception;

import com.weather.sensor.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static class TargetWithUsername {
        private String username;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    @Test
    void sensorNotFoundReturns404() {
        var response = handler.handleSensorNotFound(new SensorNotFoundException("s1"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertBody(response.getBody(), 404, "Sensor not found: s1");
    }

    @Test
    void sensorAlreadyExistsReturns409() {
        var response = handler.handleSensorAlreadyExists(new SensorAlreadyExistsException("s1"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertBody(response.getBody(), 409, "Sensor already exists: s1");
    }

    @Test
    void badCredentialsReturns401() {
        var response = handler.handleBadCredentials(new BadCredentialsException("Bad credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertBody(response.getBody(), 401, "Bad credentials");
    }

    @Test
    void unhandledExceptionReturns500WithoutLeakingMessage() {
        var response = handler.handleAll(new RuntimeException("secret internal detail"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void validationExceptionReturns400WithFieldErrors() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new TargetWithUsername(), "target");
        bindingResult.rejectValue("username", "NotBlank", "must not be blank");
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("username"));
    }

    @Test
    void httpMessageNotReadableReturns400() {
        var ex = new HttpMessageNotReadableException(
                "bad request", new RuntimeException("bad JSON"), new MockHttpInputMessage(new byte[0]));

        var response = handler.handleUnreadable(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
    }

    private void assertBody(ErrorResponse body, int status, String message) {
        assertNotNull(body);
        assertEquals(status, body.status());
        assertEquals(message, body.message());
        assertNotNull(body.timestamp());
    }
}
