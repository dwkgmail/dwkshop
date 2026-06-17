package com.dwkshop.backend.order;

import java.time.Duration;
import java.util.Optional;

interface SettlementSessionStore {

    void save(String token, SettlementSession session, Duration ttl);

    Optional<SettlementSession> consume(String token);
}
