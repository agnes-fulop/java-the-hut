package com.weather.sensor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableConfigurationProperties(SecurityUsersProperties.class)
public class InMemoryUsersConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityUsersProperties props, PasswordEncoder encoder) {
        var admin = User.builder()
                .username(props.admin().username())
                .password(encoder.encode(props.admin().password()))
                .roles("ADMIN")
                .build();
        var weatherman = User.builder()
                .username(props.weatherman().username())
                .password(encoder.encode(props.weatherman().password()))
                .roles("WEATHERMAN")
                .build();
        return new InMemoryUserDetailsManager(admin, weatherman);
    }
}
