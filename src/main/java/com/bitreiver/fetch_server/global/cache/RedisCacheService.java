package com.bitreiver.fetch_server.global.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {
    private final ObjectMapper objectMapper;
    
    @Qualifier("stringRedisReadTemplate")
    private final StringRedisTemplate stringRedisReadTemplate;
    
    private final StringRedisTemplate stringRedisTemplate;
    
    @Value("${cache.coin.ttl:3600}")
    private long defaultTtl;
    
    public <T> Optional<T> get(String key, Class<T> clazz) {
        try {
            String cachedValue = stringRedisReadTemplate.opsForValue().get(key);
            if (cachedValue != null) {
                T value = objectMapper.readValue(cachedValue, clazz);
                return Optional.of(value);
            }
            log.debug("캐시 미스 - key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("캐시 조회 중 오류 발생 - key: {}, error: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
    
    public <T> Optional<T> get(String key, TypeReference<T> typeReference) {
        try {
            String cachedValue = stringRedisReadTemplate.opsForValue().get(key);
            if (cachedValue != null) {
                T value = objectMapper.readValue(cachedValue, typeReference);
                return Optional.of(value);
            }
            log.debug("캐시 미스 - key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("캐시 조회 중 오류 발생 - key: {}, error: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
    
    public void set(String key, Object value) {
        set(key, value, defaultTtl);
    }
    
    public void set(String key, Object value, long ttlSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            if (ttlSeconds < 0) {
                stringRedisTemplate.opsForValue().set(key, jsonValue);
                log.debug("캐시 저장 완료 (TTL 없음) - key: {}", key);
            } else {
                stringRedisTemplate.opsForValue().set(key, jsonValue, Duration.ofSeconds(ttlSeconds));
                log.debug("캐시 저장 완료 - key: {}, ttl: {}초", key, ttlSeconds);
            }
        } catch (Exception e) {
            log.warn("캐시 저장 중 오류 발생 - key: {}, error: {}", key, e.getMessage());
        }
    }
    
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
            log.debug("캐시 삭제 완료 - key: {}", key);
        } catch (Exception e) {
            log.warn("캐시 삭제 중 오류 발생 - key: {}, error: {}", key, e.getMessage());
        }
    }
    
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("캐시 존재 확인 중 오류 발생 - key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }
}
