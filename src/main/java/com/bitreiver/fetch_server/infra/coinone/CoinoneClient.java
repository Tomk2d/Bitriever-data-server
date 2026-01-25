package com.bitreiver.fetch_server.infra.coinone;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class CoinoneClient {
    
    private final WebClient coinoneWebClient;
    private final ObjectMapper objectMapper;
    
    @Value("${external.coinone.api.url:https://api.coinone.co.kr}")
    private String baseUrl;
    
    public CoinoneClient(@Qualifier("coinoneWebClient") WebClient coinoneWebClient, ObjectMapper objectMapper) {
        this.coinoneWebClient = coinoneWebClient;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 코인원 API POST 요청
     * 
     * @param endpoint API 엔드포인트 (예: "/v2.1/account/balance/all")
     * @param accessToken 액세스 토큰
     * @param secretKey 시크릿 키
     * @param requestBody 요청 본문 (Map 형태)
     * @return API 응답 (Object)
     */
    public Mono<Object> post(String endpoint, String accessToken, String secretKey, Map<String, Object> requestBody) {
        try {
            // nonce 생성 (UUID v4)
            String nonce = UUID.randomUUID().toString();
            
            // Request Body에 nonce 추가
            Map<String, Object> body = new HashMap<>(requestBody);
            body.put("access_token", accessToken);
            body.put("nonce", nonce);
            
            // JSON 문자열로 변환
            String jsonBody = objectMapper.writeValueAsString(body);
            
            // Base64 인코딩
            String payload = Base64.getEncoder().encodeToString(jsonBody.getBytes(StandardCharsets.UTF_8));
            
            // HMAC-SHA512 서명 생성
            String signature = generateHmacSha512(payload, secretKey);
            
            // API 요청
            return coinoneWebClient.post()
                .uri(baseUrl + endpoint)
                .header("X-COINONE-PAYLOAD", payload)
                .header("X-COINONE-SIGNATURE", signature)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Coinone API 요청 실패: {}", error.getMessage()));
                
        } catch (Exception e) {
            log.error("CoinoneClient POST 요청 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * 일봉 차트 데이터 조회 (공개 API)
     * 
     * @param targetCurrency 조회할 코인 심볼 (예: BTC)
     * @param timestamp 마지막 캔들 타임스탬프 (UTC, Unix time ms), null이면 최신 데이터
     * @param size 조회할 캔들 수 (최소 1 ~ 최대 500)
     * @return 차트 데이터
     */
    public Mono<Map<String, Object>> getDailyChart(String targetCurrency, Long timestamp, int size) {
        try {
            String endpoint = "/public/v2/chart/KRW/" + targetCurrency;
            
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + endpoint)
                .queryParam("interval", "1d")
                .queryParam("size", Math.min(Math.max(size, 1), 500));
            
            if (timestamp != null) {
                builder.queryParam("timestamp", timestamp);
            }
            
            return coinoneWebClient.get()
                .uri(builder.build().toUri())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> log.error("Coinone 일봉 차트 조회 실패 - {}: {}", targetCurrency, error.getMessage()));
                
        } catch (Exception e) {
            log.error("Coinone 일봉 차트 조회 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * HMAC-SHA512 서명 생성
     * 
     * @param payload Base64 인코딩된 payload
     * @param secretKey 시크릿 키
     * @return hexdigest 형식의 서명
     */
    private String generateHmacSha512(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // hexdigest로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("HMAC-SHA512 서명 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("HMAC-SHA512 서명 생성 실패", e);
        }
    }
}
