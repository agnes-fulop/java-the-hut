package com.weather.sensor.dto.response;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {}
