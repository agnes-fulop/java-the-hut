package com.weather.sensor.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-32-bytes-long-for-hmac256");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", 86400000L);
    }

    @Test
    void generatedTokenIsValidAndContainsUsername() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String token = tokenProvider.generateToken(auth);

        assertTrue(tokenProvider.validateToken(token));
        assertEquals("admin", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void tamperedTokenFailsValidation() {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", null, List.of());
        String token = tokenProvider.generateToken(auth);
        // Replace last 6 chars of the signature segment to corrupt the HMAC
        String tampered = token.substring(0, token.length() - 6) + "XXXXXX";

        assertFalse(tokenProvider.validateToken(tampered));
    }

    @Test
    void expiredTokenFailsValidation() {
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", -1000L);
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", null, List.of());
        String token = tokenProvider.generateToken(auth);

        assertFalse(tokenProvider.validateToken(token));
    }
}
