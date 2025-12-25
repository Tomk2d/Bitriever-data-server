package com.bitreiver.fetch_server.infra.upbit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UpbitClientTest {

    @Autowired
    private UpbitClient upbitClient;

    @Test
    @DisplayName("UpbitClient 빈 등록 확인")
    void upbitClient_bean_registered() {
        assertNotNull(upbitClient, "UpbitClient가 빈으로 등록되어야 합니다.");
    }

    @Test
    @DisplayName("UpbitClient - 빈 파라미터로 호출 (에러 없이 처리)")
    void get_withEmptyParams() {
        // 빈 파라미터로 호출 시도 (에러가 발생하지 않아야 함)
        Map<String, Object> emptyParams = new HashMap<>();
        
        // 공개 API이므로 에러 없이 응답이 오거나, 네트워크 에러가 발생할 수 있음
        // 여기서는 Mono가 정상적으로 생성되는지만 확인
        assertDoesNotThrow(() -> {
            upbitClient.get("/v1/markets", null, null, emptyParams, false);
        }, "빈 파라미터로 호출 시 예외가 발생하지 않아야 합니다.");
    }
}

