package com.aisa.auth;

import com.aisa.auth.config.AuthTokenProperties;
import com.aisa.auth.config.OAuth2ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AuthTokenProperties.class, OAuth2ProviderProperties.class})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
