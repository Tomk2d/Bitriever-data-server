package com.bitreiver.fetch_server.global.config;

import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {
    @Value("${external.upbit.api.url:https://api.upbit.com}")
    private String upbitApiUrl;
    
    @Value("${external.alternative.me.api.url:https://alternative.me}")
    private String alternativeMeApiUrl;
    
    @Value("${external.binance.api.url:https://fapi.binance.com}")
    private String binanceApiUrl;

    @Bean
    public WebClient upbitWebClient() {
        // 연결 풀 설정
        ConnectionProvider connectionProvider = ConnectionProvider.builder("upbit")
            .maxConnections(50)
            .maxIdleTime(Duration.ofSeconds(10))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .evictInBackground(Duration.ofSeconds(10))
            .build();

        // EventLoopGroup 명시적 설정
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .runOn(eventLoopGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(5))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(2, TimeUnit.SECONDS))
            );
        
        return WebClient.builder()
            .baseUrl(upbitApiUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public WebClient alternativeMeWebClient(ObjectMapper objectMapper) {
        // 연결 풀 설정
        ConnectionProvider connectionProvider = ConnectionProvider.builder("alternative-me")
            .maxConnections(20)
            .maxIdleTime(Duration.ofSeconds(10))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .evictInBackground(Duration.ofSeconds(10))
            .build();

        // EventLoopGroup 명시적 설정
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .runOn(eventLoopGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
            );
        
        return WebClient.builder()
            .baseUrl(alternativeMeApiUrl)
            .codecs(configurer -> {
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
            })
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public WebClient binanceWebClient(ObjectMapper objectMapper) {
        // 연결 풀 설정
        ConnectionProvider connectionProvider = ConnectionProvider.builder("binance")
            .maxConnections(50)
            .maxIdleTime(Duration.ofSeconds(10))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .evictInBackground(Duration.ofSeconds(10))
            .build();

        // EventLoopGroup 명시적 설정
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .runOn(eventLoopGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
            );
        
        return WebClient.builder()
            .baseUrl(binanceApiUrl)
            .codecs(configurer -> {
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
            })
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}

