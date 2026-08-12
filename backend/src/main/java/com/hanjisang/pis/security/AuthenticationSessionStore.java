package com.hanjisang.pis.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AuthenticationSessionStore {

    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(AuthenticatedUser user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(user, Instant.now().plus(SESSION_TTL)));
        return token;
    }

    public AuthenticatedUser find(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.user();
    }

    public void remove(String token) {
        if (token != null) sessions.remove(token);
    }

    public void removeForUser(UUID userId, String tokenToKeep) {
        if (userId == null) return;
        sessions.entrySet().removeIf(entry -> entry.getValue().user().userId().equals(userId)
                && (tokenToKeep == null || !entry.getKey().equals(tokenToKeep)));
    }

    private record Session(AuthenticatedUser user, Instant expiresAt) { }
}
