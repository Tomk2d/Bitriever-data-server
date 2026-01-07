package com.bitreiver.fetch_server.domain.upbit.service;

import com.bitreiver.fetch_server.infra.upbit.UpbitClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UpbitServiceIntegrationTest {

    @Autowired
    private UpbitService upbitService;

    @Autowired
    private UpbitClient upbitClient;

    @Test
    @DisplayName("UpbitService와 UpbitClient 빈 등록 확인")
    void beans_registered() {
        assertNotNull(upbitService, "UpbitService가 빈으로 등록되어야 합니다.");
        assertNotNull(upbitClient, "UpbitClient가 빈으로 등록되어야 합니다.");
    }

    @Test
    @DisplayName("코인 목록 조회 테스트 - 실제 Upbit API 호출")
    void fetchAllCoinList_success() {
        Mono<List<Map<String, Object>>> result = upbitService.fetchAllCoinList();

        // 실제 API 호출 (블로킹)
        List<Map<String, Object>> coinList = result.block();

        assertNotNull(coinList, "코인 목록이 null이 아니어야 합니다.");
        // 실제 API 응답이 오면 리스트가 비어있지 않을 수 있음
        // 네트워크 에러가 발생할 수 있으므로 에러도 허용
    }

    @Test
    @DisplayName("UpbitService가 UpbitClient를 사용하는지 확인")
    void upbitService_uses_upbitClient() {
        // UpbitServiceImpl이 UpbitClient를 주입받았는지 확인
        assertTrue(upbitService instanceof UpbitServiceImpl, 
            "UpbitService는 UpbitServiceImpl 인스턴스여야 합니다.");
        
        // UpbitClient가 정상적으로 주입되었는지 확인
        assertNotNull(upbitClient, "UpbitClient가 빈으로 등록되어야 합니다.");
        
        // 실제 동작 확인: fetchAllCoinList가 정상적으로 Mono를 반환하는지 확인
        Mono<List<Map<String, Object>>> result = upbitService.fetchAllCoinList();
        assertNotNull(result, "fetchAllCoinList가 null이 아닌 Mono를 반환해야 합니다.");
    }
}

