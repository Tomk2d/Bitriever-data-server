package com.bitreiver.fetch_server.domain.economicEvent;

import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEventValue;
import com.bitreiver.fetch_server.domain.economicEvent.repository.EconomicEventRepository;
import com.bitreiver.fetch_server.domain.economicEvent.service.EconomicEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EconomicEventServiceIntegrationTest {

    @Autowired
    private EconomicEventService economicEventService;

    @Autowired
    private EconomicEventRepository economicEventRepository;

    @BeforeEach
    void setUp() {
        // 테스트 전 데이터 정리
        economicEventRepository.deleteAll();
    }

    @Test
    @DisplayName("실제 API 호출 - 2026-01 월별 데이터 수집 테스트")
    void fetchAndSaveMonthlyData_integrationTest() {
        System.out.println("\n=== 실제 API 호출 통합 테스트 시작 ===\n");

        // 테스트 실행
        assertDoesNotThrow(() -> {
            int savedCount = economicEventService.fetchAndSaveMonthlyData("2026-01");
            System.out.println("✓ 저장된 이벤트 수: " + savedCount);
            
            assertTrue(savedCount > 0, "최소한 1개 이상의 이벤트가 저장되어야 합니다.");
        }, "API 호출이 예외 없이 완료되어야 합니다.");

        // DB에서 확인
        List<EconomicEvent> savedEvents = economicEventRepository.findAll();
        assertNotNull(savedEvents, "저장된 이벤트 목록이 null이 아니어야 합니다.");
        assertFalse(savedEvents.isEmpty(), "저장된 이벤트가 있어야 합니다.");

        // 저장된 이벤트 검증
        for (EconomicEvent event : savedEvents) {
            assertNotNull(event.getUniqueName(), "uniqueName은 null이 아니어야 합니다.");
            assertNotNull(event.getTitle(), "title은 null이 아니어야 합니다.");
            assertNotNull(event.getEventDate(), "eventDate는 null이 아니어야 합니다.");
            assertNotNull(event.getCountryType(), "countryType은 null이 아니어야 합니다.");
            assertTrue(
                "us".equals(event.getCountryType()) || "kr".equals(event.getCountryType()),
                "countryType은 'us' 또는 'kr'이어야 합니다. 실제: " + event.getCountryType()
            );
            
            // EconomicEventValue가 있는 경우 검증
            if (event.getEconomicEventValue() != null) {
                EconomicEventValue value = event.getEconomicEventValue();
                assertNotNull(value.getRic(), "ric은 null이 아니어야 합니다.");
                assertNotNull(value.getEconomicEvent(), "economicEvent 관계가 설정되어야 합니다.");
                assertEquals(event.getId(), value.getEconomicEvent().getId(), "양방향 관계가 올바르게 설정되어야 합니다.");
            }
            
            System.out.println("  - " + event.getTitle() + " (" + event.getEventDate() + ")");
        }

        System.out.println("\n✓ 총 " + savedEvents.size() + "개 이벤트 저장 확인");
        System.out.println("\n=== 실제 API 호출 통합 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("ECONOMIC 그룹만 필터링되는지 확인")
    void fetchAndSaveMonthlyData_onlyEconomicGroup() {
        System.out.println("\n=== ECONOMIC 그룹 필터링 확인 테스트 시작 ===\n");

        // 테스트 실행
        int savedCount = economicEventService.fetchAndSaveMonthlyData("2026-01");
        
        assertTrue(savedCount > 0, "최소한 1개 이상의 이벤트가 저장되어야 합니다.");

        // 모든 저장된 이벤트가 ECONOMIC 그룹인지 확인
        List<EconomicEvent> allEvents = economicEventRepository.findAll();
        for (EconomicEvent event : allEvents) {
            // uniqueName에 "ECONOMIC"이 포함되어야 함
            assertTrue(
                event.getUniqueName().contains("ECONOMIC"),
                "모든 이벤트는 ECONOMIC 그룹이어야 합니다: " + event.getUniqueName()
            );
        }

        System.out.println("✓ 저장된 " + allEvents.size() + "개 이벤트 모두 ECONOMIC 그룹 확인");
        System.out.println("\n=== ECONOMIC 그룹 필터링 확인 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("중복 데이터 업데이트 확인")
    void fetchAndSaveMonthlyData_updateOnDuplicate() {
        System.out.println("\n=== 중복 데이터 업데이트 확인 테스트 시작 ===\n");

        // 첫 번째 저장
        int firstSaved = economicEventService.fetchAndSaveMonthlyData("2026-01");
        long firstCount = economicEventRepository.count();
        
        System.out.println("✓ 첫 번째 저장: " + firstSaved + "개");
        assertTrue(firstSaved > 0, "최소한 1개 이상의 이벤트가 저장되어야 합니다.");
        
        // 첫 번째 저장 시점의 updatedAt 기록
        List<EconomicEvent> firstEvents = economicEventRepository.findAll();
        assertFalse(firstEvents.isEmpty(), "저장된 이벤트가 있어야 합니다.");

        // 두 번째 저장 (중복 - 업데이트 발생)
        int secondSaved = economicEventService.fetchAndSaveMonthlyData("2026-01");
        long secondCount = economicEventRepository.count();
        
        System.out.println("✓ 두 번째 저장 (업데이트): " + secondSaved + "개");

        // 검증
        // 업데이트가 발생했으면 secondSaved는 업데이트된 개수만큼 0보다 크거나 같아야 함
        assertTrue(secondSaved >= 0, "업데이트된 개수는 0 이상이어야 합니다. (실제: " + secondSaved + ")");
        assertEquals(firstCount, secondCount, "저장된 이벤트 수가 동일해야 합니다. (중복 저장 없음)");
        
        // 실제로 업데이트가 발생했는지 확인 (updatedAt이 변경되었는지)
        List<EconomicEvent> secondEvents = economicEventRepository.findAll();
        boolean hasUpdate = false;
        for (EconomicEvent event : secondEvents) {
            EconomicEvent firstEvent = firstEvents.stream()
                .filter(e -> e.getId().equals(event.getId()))
                .findFirst()
                .orElse(null);
            
            if (firstEvent != null && event.getUpdatedAt() != null && firstEvent.getUpdatedAt() != null) {
                // updatedAt이 변경되었거나, secondSaved가 0보다 크면 업데이트 발생
                if (event.getUpdatedAt().isAfter(firstEvent.getUpdatedAt()) || secondSaved > 0) {
                    hasUpdate = true;
                    break;
                }
            }
        }
        
        // 업데이트가 발생했거나, secondSaved가 0보다 크면 성공
        assertTrue(hasUpdate || secondSaved > 0, 
            "중복된 데이터는 업데이트되어야 합니다. (updatedAt 변경 또는 업데이트 개수 > 0)");

        System.out.println("✓ 중복 데이터 업데이트 정상 동작 확인");
        System.out.println("✓ 저장된 이벤트 수: " + firstCount + "개 (변경 없음)");
        System.out.println("✓ 업데이트 발생: " + (hasUpdate || secondSaved > 0));
        System.out.println("\n=== 중복 데이터 업데이트 확인 테스트 완료 ===\n");
    }
}
