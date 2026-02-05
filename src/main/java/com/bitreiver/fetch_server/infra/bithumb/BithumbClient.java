package com.bitreiver.fetch_server.infra.bithumb;

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
public class BithumbClient {

    private final WebClient bithumbWebClient;

    @Value("${external.bithumb.api.url:https://api.bithumb.com}")
    private String baseUrl;

    public BithumbClient(@Qualifier("bithumbWebClient") WebClient bithumbWebClient) {
        this.bithumbWebClient = bithumbWebClient;
    }

    /**
     * Bithumb private API GET 요청 (JWT Bearer 인증)
     * 문서: access_key, nonce, timestamp(ms) 필수, 파라미터 있을 때 query_hash(SHA512), query_hash_alg
     */
    public Mono<Object> get(String endpoint, String accessKey, String secretKey, Map<String, Object> params, boolean requireAuth) {
        try {
            WebClient.RequestHeadersSpec<?> requestSpec;

            if (requireAuth) {
                String queryStringForJwt = buildQueryString(params);
                String jwtToken = createJwtToken(accessKey, secretKey, queryStringForJwt);

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

                requestSpec = bithumbWebClient.get()
                    .uri(URI.create(actualUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            } else {
                requestSpec = bithumbWebClient.get()
                    .uri(uriBuilder -> {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + endpoint);
                        if (params != null) {
                            params.forEach((key, value) -> {
                                if (value instanceof List) {
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
                .doOnError(error -> log.error("Bithumb API 요청 실패: {}", error.getMessage()));
        } catch (Exception e) {
            log.error("BithumbClient GET 요청 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * Bithumb JWT: access_key, nonce, timestamp(ms) 필수. 파라미터 있으면 query_hash(SHA512), query_hash_alg
     */
    private String createJwtToken(String accessKey, String secretKey, String queryString) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("access_key", accessKey);
            payload.put("nonce", UUID.randomUUID().toString());
            payload.put("timestamp", System.currentTimeMillis());

            if (queryString != null && !queryString.isEmpty()) {
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
            log.error("Bithumb JWT 토큰 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Bithumb JWT 토큰 생성 실패", e);
        }
    }

    /** 배열 파라미터: key[]=value1&key[]=value2 형태 (Bithumb 문서) */
    private String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    pairs.add(key + "=" + String.valueOf(item));
                }
            } else {
                pairs.add(key + "=" + String.valueOf(value));
            }
        }
        return String.join("&", pairs);
    }
}
