package com.bitreiver.fetch_server.domain.economicIndex.service;

import com.bitreiver.fetch_server.domain.economicIndex.dto.EconomicIndexRedisDto;
import com.bitreiver.fetch_server.domain.economicIndex.enums.EconomicIndexType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EconomicIndexServiceIntegrationTest {

    @Autowired
    private EconomicIndexService economicIndexService;

    @Test
    @DisplayName("단일 지표 수집 및 캐싱 테스트 - KOSPI")
    void fetchAndCacheByIndexType_kospi() {
        System.out.println("\n=== KOSPI 지표 수집 테스트 시작 ===\n");
        
        // 실행
        assertDoesNotThrow(() -> {
            economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.KOSPI);
        }, "KOSPI 지표 수집이 예외 없이 완료되어야 합니다.");

        // Redis에서 조회하여 검증
        List<EconomicIndexRedisDto> data = economicIndexService.getByIndexType(EconomicIndexType.KOSPI);
        
        assertNotNull(data, "데이터가 null이 아니어야 합니다.");
        assertFalse(data.isEmpty(), "데이터가 비어있지 않아야 합니다.");
        // 5분 간격 1일치 데이터는 장 운영 시간에 따라 개수가 달라질 수 있음
        // 최소한의 데이터는 있어야 하므로 10개 이상으로 완화
        assertTrue(data.size() >= 10, "1일치(5분 간격) 데이터이므로 최소 10개 이상이어야 합니다. 실제: " + data.size());
        
        // 첫 번째 데이터 검증
        EconomicIndexRedisDto first = data.get(0);
        assertNotNull(first.getDateTime(), "날짜 시간이 null이 아니어야 합니다.");
        assertNotNull(first.getPrice(), "가격이 null이 아니어야 합니다.");
        assertNotNull(first.getDateTimeString(), "날짜 시간 문자열이 null이 아니어야 합니다.");
        assertTrue(first.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0, "가격이 0보다 커야 합니다.");
        
        // 마지막 데이터 검증
        EconomicIndexRedisDto last = data.get(data.size() - 1);
        assertNotNull(last.getDateTime(), "마지막 날짜 시간이 null이 아니어야 합니다.");
        assertNotNull(last.getPrice(), "마지막 가격이 null이 아니어야 합니다.");
        
        // 날짜 시간 순서 검증 (오름차순)
        for (int i = 1; i < data.size(); i++) {
            assertTrue(
                data.get(i).getDateTime().isAfter(data.get(i - 1).getDateTime()) || 
                data.get(i).getDateTime().isEqual(data.get(i - 1).getDateTime()),
                "날짜 시간이 오름차순으로 정렬되어야 합니다."
            );
        }
        
        System.out.println("✓ 수집된 데이터 개수: " + data.size());
        System.out.println("✓ 첫 번째 데이터: " + first.getDateTime() + " - " + first.getPrice());
        System.out.println("✓ 마지막 데이터: " + last.getDateTime() + " - " + last.getPrice());
        System.out.println("\n=== KOSPI 지표 수집 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("단일 지표 수집 및 캐싱 테스트 - NASDAQ")
    void fetchAndCacheByIndexType_nasdaq() {
        System.out.println("\n=== NASDAQ 지표 수집 테스트 시작 ===\n");
        
        assertDoesNotThrow(() -> {
            economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.NASDAQ);
        });

        List<EconomicIndexRedisDto> data = economicIndexService.getByIndexType(EconomicIndexType.NASDAQ);
        
        assertNotNull(data);
        assertFalse(data.isEmpty());
        // 5분 간격 1일치 데이터는 장 운영 시간에 따라 개수가 달라질 수 있음
        assertTrue(data.size() >= 10, "1일치(5분 간격) 데이터이므로 최소 10개 이상이어야 합니다. 실제: " + data.size());
        
        System.out.println("✓ NASDAQ 데이터 개수: " + data.size());
        System.out.println("\n=== NASDAQ 지표 수집 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("단일 지표 수집 및 캐싱 테스트 - USD/KRW 환율")
    void fetchAndCacheByIndexType_usdKrw() {
        System.out.println("\n=== USD/KRW 환율 수집 테스트 시작 ===\n");
        
        assertDoesNotThrow(() -> {
            economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.USD_KRW);
        });

        List<EconomicIndexRedisDto> data = economicIndexService.getByIndexType(EconomicIndexType.USD_KRW);
        
        assertNotNull(data);
        assertFalse(data.isEmpty());
        // 5분 간격 1일치 데이터는 장 운영 시간에 따라 개수가 달라질 수 있음
        assertTrue(data.size() >= 10, "1일치(5분 간격) 데이터이므로 최소 10개 이상이어야 합니다. 실제: " + data.size());
        
        // 환율은 보통 1000~2000 사이
        EconomicIndexRedisDto first = data.get(0);
        assertTrue(
            first.getPrice().compareTo(java.math.BigDecimal.valueOf(1000)) > 0 &&
            first.getPrice().compareTo(java.math.BigDecimal.valueOf(2000)) < 0,
            "USD/KRW 환율은 보통 1000~2000 사이입니다."
        );
        
        System.out.println("✓ USD/KRW 데이터 개수: " + data.size());
        System.out.println("✓ 현재 환율: " + first.getPrice());
        System.out.println("\n=== USD/KRW 환율 수집 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("모든 지표 수집 테스트")
    void fetchAndCacheAll() {
        System.out.println("\n=== 모든 지표 수집 테스트 시작 ===\n");
        
        // 실행
        assertDoesNotThrow(() -> {
            economicIndexService.fetchAndCacheAll();
        }, "모든 지표 수집이 예외 없이 완료되어야 합니다.");

        // 각 지표별로 데이터 확인
        int totalDataCount = 0;
        for (EconomicIndexType type : EconomicIndexType.values()) {
            List<EconomicIndexRedisDto> data = economicIndexService.getByIndexType(type);
            assertNotNull(data, type + " 데이터가 null이 아니어야 합니다.");
            assertFalse(data.isEmpty(), type + " 데이터가 비어있지 않아야 합니다.");
            // 5분 간격 1일치 데이터는 장 운영 시간에 따라 개수가 달라질 수 있음
            assertTrue(data.size() >= 10, type + " 데이터는 1일치(5분 간격) 기준 최소 10개 이상이어야 합니다. 실제: " + data.size());
            
            totalDataCount += data.size();
            System.out.println("✓ " + type + " 데이터 개수: " + data.size());
        }
        
        System.out.println("✓ 총 데이터 개수: " + totalDataCount);
        System.out.println("\n=== 모든 지표 수집 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("기간별 조회 테스트")
    void getByIndexTypeAndDateRange() {
        System.out.println("\n=== 기간별 조회 테스트 시작 ===\n");
        
        // 먼저 데이터 수집
        economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.KOSPI);
        
        // 최근 1개월 데이터 조회
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        
        List<EconomicIndexRedisDto> data = economicIndexService.getByIndexTypeAndDateRange(
            EconomicIndexType.KOSPI, startDate, endDate
        );
        
        assertNotNull(data, "데이터가 null이 아니어야 합니다.");
        assertFalse(data.isEmpty(), "데이터가 비어있지 않아야 합니다.");
        
        // 날짜 범위 검증
        for (EconomicIndexRedisDto dto : data) {
            LocalDate date = dto.getDateTime().toLocalDate();
            assertTrue(
                (date.isEqual(startDate) || date.isAfter(startDate)) &&
                (date.isEqual(endDate) || date.isBefore(endDate)),
                "날짜가 범위 내에 있어야 합니다: " + date + " (범위: " + startDate + " ~ " + endDate + ")"
            );
        }
        
        System.out.println("✓ 조회 기간: " + startDate + " ~ " + endDate);
        System.out.println("✓ 조회된 데이터 개수: " + data.size());
        if (!data.isEmpty()) {
            System.out.println("✓ 첫 번째: " + data.get(0).getDateTime() + " - " + data.get(0).getPrice());
            System.out.println("✓ 마지막: " + data.get(data.size() - 1).getDateTime() + " - " + 
                data.get(data.size() - 1).getPrice());
        }
        System.out.println("\n=== 기간별 조회 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("Redis 캐싱 확인 테스트")
    void redisCachingTest() {
        System.out.println("\n=== Redis 캐싱 확인 테스트 시작 ===\n");
        
        // 첫 번째 조회 (캐시 없음)
        economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.KOSPI);
        List<EconomicIndexRedisDto> firstCall = economicIndexService.getByIndexType(EconomicIndexType.KOSPI);
        
        // 두 번째 조회 (캐시에서)
        List<EconomicIndexRedisDto> secondCall = economicIndexService.getByIndexType(EconomicIndexType.KOSPI);
        
        // 데이터 일치 확인
        assertNotNull(firstCall);
        assertNotNull(secondCall);
        assertEquals(firstCall.size(), secondCall.size(), "캐시된 데이터와 조회된 데이터의 크기가 같아야 합니다.");
        
        // 첫 번째 데이터 비교
        if (!firstCall.isEmpty() && !secondCall.isEmpty()) {
            EconomicIndexRedisDto first = firstCall.get(0);
            EconomicIndexRedisDto second = secondCall.get(0);
            
            assertEquals(first.getDateTime(), second.getDateTime(), "날짜 시간이 일치해야 합니다.");
            assertEquals(first.getPrice(), second.getPrice(), "가격이 일치해야 합니다.");
        }
        
        System.out.println("✓ 첫 번째 조회 데이터 개수: " + firstCall.size());
        System.out.println("✓ 두 번째 조회 데이터 개수: " + secondCall.size());
        System.out.println("✓ 캐시 동작 정상 확인");
        System.out.println("\n=== Redis 캐싱 확인 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("데이터 정합성 검증 테스트")
    void dataIntegrityTest() {
        System.out.println("\n=== 데이터 정합성 검증 테스트 시작 ===\n");
        
        economicIndexService.fetchAndCacheByIndexType(EconomicIndexType.KOSPI);
        List<EconomicIndexRedisDto> data = economicIndexService.getByIndexType(EconomicIndexType.KOSPI);
        
        assertNotNull(data);
        assertFalse(data.isEmpty());
        
        // 모든 데이터 검증
        for (EconomicIndexRedisDto dto : data) {
            assertNotNull(dto.getDateTime(), "날짜 시간은 null이 아니어야 합니다.");
            assertNotNull(dto.getDateTimeString(), "날짜 시간 문자열은 null이 아니어야 합니다.");
            assertNotNull(dto.getPrice(), "가격은 null이 아니어야 합니다.");
            assertTrue(dto.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0, "가격은 0보다 커야 합니다.");
            
            // 날짜 시간 문자열 형식 검증 (YYYY-MM-DD HH:mm:ss)
            assertTrue(
                dto.getDateTimeString().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "날짜 시간 문자열 형식이 올바르지 않습니다: " + dto.getDateTimeString()
            );
            
            // 날짜 시간과 날짜 시간 문자열 일치 확인
            assertEquals(
                dto.getDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                dto.getDateTimeString(),
                "날짜 시간과 날짜 시간 문자열이 일치해야 합니다."
            );
        }
        
        System.out.println("✓ 총 " + data.size() + "개 데이터 정합성 검증 완료");
        System.out.println("\n=== 데이터 정합성 검증 테스트 완료 ===\n");
    }
}

