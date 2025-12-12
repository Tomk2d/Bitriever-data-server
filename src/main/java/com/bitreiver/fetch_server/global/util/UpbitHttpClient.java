package com.bitreiver.fetch_server.global.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Component
public class UpbitHttpClient {
    
    private final WebClient upbitWebClient;
    
    @Value("${external.upbit.api.url:https://api.upbit.com}")
    private String baseUrl;
    
    public UpbitHttpClient(@Qualifier("upbitWebClient") WebClient upbitWebClient) {
        this.upbitWebClient = upbitWebClient;
    }
    
    public Mono<Object> get(String endpoint, String accessKey, String secretKey, Map<String, Object> params, boolean requireAuth) {
        try {
            WebClient.RequestHeadersSpec<?> requestSpec;
            
            if (requireAuth) {
                String queryStringForJwt = buildQueryString(params);
                
                // JWT 토큰 생성
                String jwtToken = createJwtToken(accessKey, secretKey, queryStringForJwt);
                
                // 실제 HTTP 요청 URL 생성 
                // 쿼리 스트링을 각 파라미터별로 URL 인코딩하여 구성
                StringBuilder urlBuilder = new StringBuilder(baseUrl + endpoint);
                if (params != null && !params.isEmpty()) {
                    urlBuilder.append("?");
                    List<String> encodedParams = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if (value instanceof List) {
                            List<?> list = (List<?>) value;
                            for (Object item : list) {
                                String paramKey = key.endsWith("[]") ? key : key + "[]";
                                encodedParams.add(URLEncoder.encode(paramKey, StandardCharsets.UTF_8) + "=" + 
                                    URLEncoder.encode(String.valueOf(item), StandardCharsets.UTF_8));
                            }
                        } else {
                            encodedParams.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + 
                                URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
                        }
                    }
                    urlBuilder.append(String.join("&", encodedParams));
                }
                String actualUrl = urlBuilder.toString();
                
                requestSpec = upbitWebClient.get()
                    .uri(URI.create(actualUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            } else {
                requestSpec = upbitWebClient.get()
                    .uri(uriBuilder -> {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + endpoint);
                        if (params != null) {
                            params.forEach((key, value) -> {
                                if (value instanceof List) {
                                    // key가 이미 "states[]" 형식이면 그대로 사용, 아니면 "[]" 추가
                                    String paramKey = key.endsWith("[]") ? key : key + "[]";
                                    ((List<?>) value).forEach(item -> builder.queryParam(paramKey, item));
                                } else {
                                    builder.queryParam(key, value);
                                }
                            });
                        }
                        return builder.build().toUri();
                    });
            }
            
            return requestSpec
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Upbit API 요청 실패: {}", error.getMessage()));
                
        } catch (Exception e) {
            log.error("UpbitHttpClient GET 요청 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    private String createJwtToken(String accessKey, String secretKey, String queryString) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("access_key", accessKey);
            payload.put("nonce", UUID.randomUUID().toString());
            
            if (queryString != null && !queryString.isEmpty()) {
                // SHA512 해시 생성 (UTF-8 바이트로 변환)
                MessageDigest digest = MessageDigest.getInstance("SHA-512");
                byte[] hash = digest.digest(queryString.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                
                payload.put("query_hash", hexString.toString());
                payload.put("query_hash_alg", "SHA512");
            }
            
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            return Jwts.builder()
                .claims(payload)
                .signWith(key)
                .compact();
                
        } catch (Exception e) {
            log.error("JWT 토큰 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("JWT 토큰 생성 실패", e);
        }
    }
    
    
    /**
     * Upbit 공식 예제에 따른 쿼리 스트링 생성
     * 공식 예제: params.entrySet().stream()
     *     .flatMap(e -> e.getValue().stream().map(v -> e.getKey() + "=" + v))
     *     .collect(Collectors.joining("&"));
     */
    private String buildQueryString(Map<String, Object> params) {
        try {
            // params가 null이거나 비어있으면 빈 문자열 반환
            if (params == null || params.isEmpty()) {
                return "";
            }
            
            List<String> pairs = new ArrayList<>();
            
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof List) {
                    // 배열 파라미터: key=value 형식 (각 항목마다)
                    // 예: "states[]=done", "states[]=cancel"
                    List<?> list = (List<?>) value;
                    for (Object item : list) {
                        pairs.add(key + "=" + String.valueOf(item));
                    }
                } else {
                    // 일반 파라미터: key=value 형식
                    pairs.add(key + "=" + String.valueOf(value));
                }
            }
            
            return String.join("&", pairs);
        } catch (Exception e) {
            log.error("쿼리 스트링 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("쿼리 스트링 생성 실패", e);
        }
    }
}

