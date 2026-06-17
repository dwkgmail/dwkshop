package com.dwkshop.backend.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
class RedisSettlementSessionStore implements SettlementSessionStore {

    private static final String PREFIX = "dwkshop:order:settlement:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisSettlementSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String token, SettlementSession session, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(session), ttl);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "订单结算信息保存失败", ex);
        }
    }

    @Override
    public Optional<SettlementSession> consume(String token) {
        String value = redisTemplate.opsForValue().getAndDelete(key(token));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, SettlementSession.class));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单结算信息已失效", ex);
        }
    }

    private String key(String token) {
        return PREFIX + token;
    }
}
