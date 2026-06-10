package com.aisa.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies the {@link PasswordEncoder} used to store passwords as adaptive
 * BCrypt hashes (Requirements 1.1, 25.3). Defined as a standalone bean rather
 * than via Spring Security's web auto-configuration so the service does not
 * impose authentication on its own endpoints at this stage.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
