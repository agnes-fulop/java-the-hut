package com.weather.sensor.exception;

public class SensorAlreadyExistsException extends RuntimeException {
    public SensorAlreadyExistsException(String sensorId) {
        super("Sensor already exists: " + sensorId);
    }
}
