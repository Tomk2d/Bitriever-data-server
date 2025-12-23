package com.bitreiver.fetch_server.domain.longshort.controller;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=bitriever0320",
    "spring.data.redis.replica.host=localhost",
    "spring.data.redis.replica.port=6379"
})
class BinanceLongShortBatchTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private CoinRepository coinRepository;

    private static final TypeReference<List<BinanceLongShortRatioResponse>> LONG_SHORT_RATIO_TYPE =
            new TypeReference<List<BinanceLongShortRatioResponse>>() {};

    @Test
    @DisplayName("수동 배치 트리거 API - 모든 period (1h, 4h, 12h, 1d) 테스트 및 Redis 캐싱 검증")
    void triggerBinanceLongShortRatio_allPeriods_success() throws Exception {
        // 활성 코인 목록 조회 (테스트 검증용)
        List<Coin> activeCoins = coinRepository.findByIsActiveTrue();
        Set<String> uniqueSymbols = activeCoins.stream()
                .map(Coin::getSymbol)
                .collect(Collectors.toSet());

        assertFalse(uniqueSymbols.isEmpty(), "활성 코인이 없습니다. 테스트를 진행할 수 없습니다.");

        String[] periods = {"1h", "4h", "12h", "1d"};

        // 각 period에 대해 배치 실행 및 Redis 캐싱 검증
        for (String period : periods) {
            // 배치 실행
            mockMvc.perform(
                            post("/test/batch/binance/long-short-ratio")
                                    .param("period", period)
                                    .param("limit", "30")
                    )
                    .andExpect(status().isOk());

            // 배치 실행 후 잠시 대기 (비동기 처리 고려)
            Thread.sleep(2000);

            // Redis에 데이터가 캐싱되었는지 확인
            boolean hasCachedData = false;
            int cachedSymbolCount = 0;

            for (String symbol : uniqueSymbols) {
                String redisKey = "binance:longShortRatio:" + symbol + ":" + period;
                Optional<List<BinanceLongShortRatioResponse>> cached = redisCacheService.get(redisKey, LONG_SHORT_RATIO_TYPE);

                if (cached.isPresent() && !cached.get().isEmpty()) {
                    hasCachedData = true;
                    cachedSymbolCount++;
                    List<BinanceLongShortRatioResponse> data = cached.get();
                    assertFalse(data.isEmpty(), 
                            String.format("period=%s, symbol=%s: Redis에 빈 리스트가 저장되었습니다.", period, symbol));
                    assertNotNull(data.get(0).getSymbol(), 
                            String.format("period=%s, symbol=%s: 데이터에 symbol이 없습니다.", period, symbol));
                }
            }

            assertTrue(hasCachedData, 
                    String.format("period=%s: Redis에 캐싱된 데이터가 없습니다. 배치가 정상적으로 실행되지 않았을 수 있습니다.", period));
            
            System.out.printf("period=%s: %d개의 심볼에 대해 데이터가 Redis에 캐싱되었습니다.%n", period, cachedSymbolCount);
        }
    }
}


