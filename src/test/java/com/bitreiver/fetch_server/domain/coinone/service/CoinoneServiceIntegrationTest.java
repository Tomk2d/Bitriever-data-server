package com.bitreiver.fetch_server.domain.coinone.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.coin.service.CoinImageService;
import com.bitreiver.fetch_server.domain.coin.service.CoinService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoinoneServiceIntegrationTest {

    @Autowired
    private CoinoneService coinoneService;

    @Autowired
    private CoinService coinService;

    @Autowired
    private CoinImageService coinImageService;

    @Autowired
    private CoinRepository coinRepository;

    @Test
    @DisplayName("CoinoneService 빈 등록 확인")
    void coinoneService_bean_registered() {
        assertNotNull(coinoneService, "CoinoneService가 빈으로 등록되어야 합니다.");
    }

    @Test
    @DisplayName("코인원 API 호출 테스트 - 실제 API 호출")
    void fetchAllCoinList_success() {
        Mono<List<Map<String, Object>>> result = coinoneService.fetchAllCoinList();

        // 실제 API 호출 (블로킹)
        List<Map<String, Object>> coinList = result.block();

        assertNotNull(coinList, "코인 목록이 null이 아니어야 합니다.");
        assertFalse(coinList.isEmpty(), "코인 목록이 비어있지 않아야 합니다.");
        
        // 첫 번째 코인 데이터 검증
        Map<String, Object> firstCoin = coinList.get(0);
        assertNotNull(firstCoin.get("symbol"), "symbol 필드가 있어야 합니다.");
        assertNotNull(firstCoin.get("exchange"), "exchange 필드가 있어야 합니다.");
        assertEquals("COINONE", firstCoin.get("exchange"), "exchange는 COINONE이어야 합니다.");
    }

    @Test
    @DisplayName("코인원 API 응답 파싱 테스트 - 필수 필드 확인")
    void fetchAllCoinList_parsing_test() {
        Mono<List<Map<String, Object>>> result = coinoneService.fetchAllCoinList();
        List<Map<String, Object>> coinList = result.block();

        assertNotNull(coinList);
        assertFalse(coinList.isEmpty());

        for (Map<String, Object> coin : coinList) {
            assertNotNull(coin.get("symbol"), "symbol은 필수입니다.");
            assertNotNull(coin.get("marketCode"), "marketCode는 필수입니다.");
            assertNotNull(coin.get("exchange"), "exchange는 필수입니다.");
            assertEquals("COINONE", coin.get("exchange"), "exchange는 COINONE이어야 합니다.");
            
            String marketCode = (String) coin.get("marketCode");
            assertTrue(marketCode.startsWith("KRW-"), "marketCode는 KRW-{symbol} 형식이어야 합니다.");
        }
    }

    @Test
    @DisplayName("코인 목록 수집 및 이미지 다운로드 통합 테스트")
    void coin_collection_and_image_download_integration_test() {
        // 1. 코인원 API에서 코인 목록 조회
        Mono<List<Map<String, Object>>> coinoneResult = coinoneService.fetchAllCoinList();
        List<Map<String, Object>> coinoneCoinList = coinoneResult.block();

        assertNotNull(coinoneCoinList);
        assertFalse(coinoneCoinList.isEmpty(), "코인원 코인 목록이 비어있지 않아야 합니다.");

        // 2. DB에 저장하기 전 업비트 코인 symbol Set 조회
        List<Coin> upbitCoins = coinRepository.findByExchange("UPBIT");
        Set<String> upbitSymbols = upbitCoins.stream()
            .filter(coin -> coin.getIsActive() != null && coin.getIsActive())
            .map(Coin::getSymbol)
            .filter(symbol -> symbol != null && !symbol.isEmpty())
            .collect(Collectors.toSet());

        // 3. 업비트에 없는 코인만 필터링
        List<Map<String, Object>> newCoinoneCoins = coinoneCoinList.stream()
            .filter(coin -> {
                String symbol = (String) coin.get("symbol");
                return symbol != null && !upbitSymbols.contains(symbol);
            })
            .collect(Collectors.toList());

        assertFalse(newCoinoneCoins.isEmpty(), 
            "업비트에 없는 코인원 코인이 있어야 합니다. (없을 수도 있지만 테스트는 진행)");

        // 4. 코인 목록 저장
        Map<String, Object> saveResult = coinService.saveAllCoinList(coinoneCoinList);
        assertNotNull(saveResult);
        assertTrue(saveResult.containsKey("new"), "결과에 'new' 키가 있어야 합니다.");

        // 5. 이미지 다운로드
        int downloadedCount = coinImageService.downloadCoinImages(coinoneCoinList);
        assertTrue(downloadedCount >= 0, "다운로드된 이미지 수는 0 이상이어야 합니다.");

        // 6. 저장된 코인 확인
        List<Coin> savedCoinoneCoins = coinRepository.findByExchange("COINONE");
        assertNotNull(savedCoinoneCoins);
    }

    @Test
    @DisplayName("업비트에 없는 종목만 필터링되는지 확인")
    void filter_coins_not_in_upbit_test() {
        // 1. DB에서 업비트 코인 symbol Set 조회
        List<Coin> upbitCoins = coinRepository.findByExchange("UPBIT");
        Set<String> upbitSymbols = upbitCoins.stream()
            .filter(coin -> coin.getIsActive() != null && coin.getIsActive())
            .map(Coin::getSymbol)
            .filter(symbol -> symbol != null && !symbol.isEmpty())
            .collect(Collectors.toSet());

        // 2. 코인원 API에서 코인 목록 조회
        Mono<List<Map<String, Object>>> coinoneResult = coinoneService.fetchAllCoinList();
        List<Map<String, Object>> coinoneCoinList = coinoneResult.block();

        assertNotNull(coinoneCoinList);
        assertFalse(coinoneCoinList.isEmpty());

        // 3. 업비트에 없는 코인만 필터링
        List<Map<String, Object>> filteredCoins = coinoneCoinList.stream()
            .filter(coin -> {
                String symbol = (String) coin.get("symbol");
                return symbol != null && !upbitSymbols.contains(symbol);
            })
            .collect(Collectors.toList());

        // 4. 필터링된 코인들이 모두 업비트에 없는지 확인
        for (Map<String, Object> coin : filteredCoins) {
            String symbol = (String) coin.get("symbol");
            assertFalse(upbitSymbols.contains(symbol), 
                "필터링된 코인은 업비트에 없어야 합니다: " + symbol);
        }

        // 5. 코인 저장
        Map<String, Object> saveResult = coinService.saveAllCoinList(coinoneCoinList);
        assertNotNull(saveResult);

        // 6. 저장된 코인원 코인 확인
        List<Coin> savedCoinoneCoins = coinRepository.findByExchange("COINONE");
        for (Coin coin : savedCoinoneCoins) {
            assertFalse(upbitSymbols.contains(coin.getSymbol()), 
                "저장된 코인원 코인은 업비트에 없어야 합니다: " + coin.getSymbol());
            assertEquals("COINONE", coin.getExchange(), 
                "저장된 코인의 exchange는 'COINONE'이어야 합니다.");
        }
    }
}
