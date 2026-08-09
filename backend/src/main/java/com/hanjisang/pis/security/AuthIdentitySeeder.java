package com.hanjisang.pis.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuthIdentitySeeder {

    private final AuthIdentityRepository repository;
    private final String testPassword;

    public AuthIdentitySeeder(AuthIdentityRepository repository,
            @Value("${pis.auth-test-password:}") String testPassword) {
        this.repository = repository;
        this.testPassword = testPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedSyntheticRuntimeAccounts() {
        if (testPassword == null || testPassword.isBlank()) return;
        repository.seedSyntheticAccounts(testPassword);
    }
}
