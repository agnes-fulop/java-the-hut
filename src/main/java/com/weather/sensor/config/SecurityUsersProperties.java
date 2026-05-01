package com.weather.sensor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.users")
public record SecurityUsersProperties(UserCredentials admin, UserCredentials weatherman) {
    public record UserCredentials(String username, String password) {}
}
