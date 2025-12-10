package com.bitreiver.fetch_server.domain.user.dto;

import com.bitreiver.fetch_server.domain.user.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 정보 응답")
public class UserResponse {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;
    
    @Schema(description = "이메일 주소", example = "user@example.com")
    private String email;
    
    @Schema(description = "닉네임", example = "user123")
    private String nickname;
    
    @Schema(description = "가입 타입 (0: 로컬, 1: SNS)", example = "0")
    private Short signupType;
    
    @Schema(description = "계정 생성 시간", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "계정 활성화 여부", example = "true")
    private Boolean isActive;
    
    @Schema(description = "거래소 연결 여부", example = "true")
    private Boolean isConnectExchange;
    
    @Schema(description = "연결된 거래소 목록", example = "[\"UPBIT\", \"BINANCE\"]")
    private List<String> connectedExchanges;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static UserResponse from(User user) {
        List<String> connectedExchanges = parseConnectedExchanges(user.getConnectedExchanges());
        
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .signupType(user.getSignupType())
            .createdAt(user.getCreatedAt())
            .isActive(user.getIsActive())
            .isConnectExchange(user.getIsConnectExchange())
            .connectedExchanges(connectedExchanges)
            .build();
    }
    
    private static List<String> parseConnectedExchanges(String connectedExchangesJson) {
        if (connectedExchangesJson == null || connectedExchangesJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(
                connectedExchangesJson, 
                new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            log.error("connected_exchanges JSON 파싱 실패: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}

