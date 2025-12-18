package com.bitreiver.fetch_server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
public class RedisConfig {
    
    // Master (쓰기)
    @Value("${spring.data.redis.host}")
    private String masterHost;

    @Value("${spring.data.redis.port}")
    private int masterPort;

    @Value("${spring.data.redis.password}")
    private String password;
    
    // Replica (읽기)
    @Value("${spring.data.redis.replica.host}")
    private String replicaHost;

    @Value("${spring.data.redis.replica.port}")
    private int replicaPort;
    
    // Master ConnectionFactory (쓰기용)
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(masterHost);
        config.setPort(masterPort);
        config.setPassword(password);
        return new LettuceConnectionFactory(config);
    }
    
    // Replica ConnectionFactory (읽기용)
    @Bean
    public RedisConnectionFactory redisReplicaConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(replicaHost);
        config.setPort(replicaPort);
        config.setPassword(password);
        return new LettuceConnectionFactory(config);
    }

    // Master RedisTemplate (쓰기용)
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
    
    // Replica RedisTemplate (읽기용)
    @Bean
    public RedisTemplate<String, Object> redisReadTemplate(RedisConnectionFactory redisReplicaConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisReplicaConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    // Master StringRedisTemplate (쓰기용)
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
    
    // Replica StringRedisTemplate (읽기용)
    @Bean
    public StringRedisTemplate stringRedisReadTemplate(RedisConnectionFactory redisReplicaConnectionFactory) {
        return new StringRedisTemplate(redisReplicaConnectionFactory);
    }

    /**
     * Redis용 ObjectMapper (WebClient 등에서 사용)
     * Spring MVC는 JacksonConfig의 @Primary ObjectMapper를 사용
     */
    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
