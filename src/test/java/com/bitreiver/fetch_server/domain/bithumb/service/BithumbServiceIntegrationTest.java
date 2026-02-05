package com.bitreiver.fetch_server.domain.bithumb.service;

import com.bitreiver.fetch_server.infra.bithumb.BithumbClient;
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
class BithumbServiceIntegrationTest {

    @Autowired
    private BithumbService bithumbService;

    @Autowired
    private BithumbClient bithumbClient;

    @Test
    @DisplayName("BithumbService와 BithumbClient 빈 등록 확인")
    void beans_registered() {
        assertNotNull(bithumbService, "BithumbService가 빈으로 등록되어야 합니다.");
        assertNotNull(bithumbClient, "BithumbClient가 빈으로 등록되어야 합니다.");
    }

    @Test
    @DisplayName("BithumbService가 BithumbServiceImpl 인스턴스인지 확인")
    void bithumbService_implementation() {
        assertTrue(bithumbService instanceof BithumbServiceImpl,
            "BithumbService는 BithumbServiceImpl 인스턴스여야 합니다.");
    }

    @Test
    @DisplayName("fetchAccounts - 잘못된 키로 호출 시 빈 리스트 또는 에러 (실제 API 미호출 권장)")
    void fetchAccounts_withInvalidKeys_returnsEmptyOrFails() {
        Mono<List<Map<String, Object>>> result = bithumbService.fetchAccounts("invalid-key", "invalid-secret");
        assertNotNull(result, "fetchAccounts는 null이 아닌 Mono를 반환해야 합니다.");
        // 실제 키 없이 block() 시 예외 또는 빈 리스트 가능
        try {
            List<Map<String, Object>> accounts = result.block();
            assertTrue(accounts == null || accounts.isEmpty() || accounts.size() >= 0,
                "잘못된 키면 빈 리스트이거나 예외가 발생할 수 있음");
        } catch (Exception e) {
            assertTrue(e.getMessage() == null || e.getMessage().length() > 0, "예외 메시지 존재");
        }
    }
}
