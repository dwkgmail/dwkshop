package com.dwkshop.backend.order;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
class InMemorySettlementSessionStore implements SettlementSessionStore {

    private final Map<String, SettlementSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(String token, SettlementSession session, Duration ttl) {
        sessions.put(token, session);
    }

    @Override
    public Optional<SettlementSession> consume(String token) {
        return Optional.ofNullable(sessions.remove(token));
    }
}
