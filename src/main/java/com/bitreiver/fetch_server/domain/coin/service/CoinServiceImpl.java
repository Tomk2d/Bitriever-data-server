package com.bitreiver.fetch_server.domain.coin.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinServiceImpl implements CoinService {
    
    private final CoinRepository coinRepository;
    
    @Override
    @Transactional
    public Map<String, Object> saveAllCoinList(List<Map<String, Object>> fetchedDataList) {
        try {
            // 1. 패치 데이터에서 활성화 코인만 추리기 (거래소별로 분리)
            Set<String> fetchedActiveUpbitMarketCodes = fetchedDataList.stream()
                .filter(data -> "UPBIT".equals(data.getOrDefault("exchange", "").toString()))
                .map(data -> {
                    String pair = data.getOrDefault("pair", "").toString();
                    return convertMarketCodeFormat(pair);
                })
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());
            
            Set<String> fetchedActiveCoinoneMarketCodes = fetchedDataList.stream()
                .filter(data -> "COINONE".equals(data.getOrDefault("exchange", "").toString()))
                .map(data -> {
                    String symbol = data.getOrDefault("symbol", "").toString();
                    return "KRW-" + symbol;
                })
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());
            
            // 2. DB에 있는 모든 UPBIT 코인 조회
            List<Coin> allUpbitCoins = coinRepository.findByExchange("UPBIT");
            // 2-1. DB에 있는 모든 UPBIT 코인의 marketCode Set 생성
            Set<String> dbSavedUpbitMarketCodes = allUpbitCoins.stream()
                .filter(coin -> coin.getIsActive() == true)
                .map(Coin::getMarketCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());
            
            // 2-2. 업비트 코인의 symbol Set 생성 (코인원과 비교용)
            Set<String> upbitSymbols = allUpbitCoins.stream()
                .filter(coin -> coin.getIsActive() == true)
                .map(Coin::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isEmpty())
                .collect(Collectors.toSet());
            
            // 2-3. DB에 있는 모든 COINONE 코인 조회
            List<Coin> allCoinoneCoins = coinRepository.findByExchange("COINONE");
            Set<String> dbSavedCoinoneMarketCodes = allCoinoneCoins.stream()
                .filter(coin -> coin.getIsActive() == true)
                .map(Coin::getMarketCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());

            // 3. 신규 저장할 코인 리스트 생성 (API에 있지만 DB에 없는 코인)
            // 3-1. 활성화할 코인 리스트 (DB에 이미 있지만 API 응답에 포함된 코인)
            List<Coin> coinsToActivate = new ArrayList<>();
            List<Coin> newCoinList = new ArrayList<>();
            Set<String> processedMarketCodes = new HashSet<>(); // 같은 배치 내 중복 방지
            
            for (Map<String, Object> fetchedData : fetchedDataList) {
                // 3-1. 업비트 코인 아니면 건너뜀.
                String exchange = fetchedData.getOrDefault("exchange", "").toString();
                if (!"UPBIT".equals(exchange)) {
                    continue;
                }
                // 3-2. 활성화 상태 아니면 건너뜀. (marketState=ACTIVE)
                String marketState = fetchedData.getOrDefault("marketState", "").toString();
                if(!("ACTIVE".equals(marketState))){
                    continue;
                }
                
                String symbol = fetchedData.getOrDefault("baseCurrencyCode", "").toString();
                String quoteCurrency = fetchedData.getOrDefault("quoteCurrencyCode", "").toString();
                String pair = fetchedData.getOrDefault("pair", "").toString();
                String marketCode = convertMarketCodeFormat(pair);
                
                // null 체크 및 빈 문자열 체크
                if (marketCode == null || marketCode.isEmpty()) {
                    continue;
                }
                
                // 같은 배치 내 중복 방지
                if (processedMarketCodes.contains(marketCode)) {
                    continue;
                }

                processedMarketCodes.add(marketCode);
                
                // 3-3. DB에 이미 있는 코인인지 확인 (전체 DB에서 체크 - marketCode는 unique)
                Optional<Coin> existingCoin = coinRepository.findByMarketCode(marketCode);
                if (existingCoin.isPresent()) {
                    // DB에 이미 있는 코인 -> API 응답에 포함되어 있으므로 활성화
                    Coin coin = existingCoin.get();
                    if ("UPBIT".equals(coin.getExchange()) && !coin.getIsActive()) {
                        coin.setIsActive(true);
                        coinsToActivate.add(coin);
                    }
                    continue;
                }
                
                // 3-4. DB에 없는 코인만 생성 -> 신규 저장 리스트에 추가
                String koreanName = fetchedData.getOrDefault("koreanName", "").toString();
                String englishName = fetchedData.getOrDefault("englishName", "").toString();
                String baseCurrencyCode = fetchedData.getOrDefault("baseCurrencyCode", "").toString();
                String imgUrl = "/data/image/" + baseCurrencyCode + ".png";
                
                Coin coin = Coin.builder()
                    .symbol(symbol)
                    .quoteCurrency(quoteCurrency)
                    .marketCode(marketCode)
                    .koreanName(koreanName)
                    .englishName(englishName)
                    .imgUrl(imgUrl)
                    .exchange("UPBIT")
                    .isActive(true)
                    .build();
                
                newCoinList.add(coin);
            }
            
            // 3-2. 코인원 코인 처리 (업비트에 없는 종목만 저장)
            List<Coin> coinoneCoinsToActivate = new ArrayList<>();
            List<Coin> newCoinoneCoinList = new ArrayList<>();
            Set<String> processedCoinoneMarketCodes = new HashSet<>();
            
            for (Map<String, Object> fetchedData : fetchedDataList) {
                String exchange = fetchedData.getOrDefault("exchange", "").toString();
                if (!"COINONE".equals(exchange)) {
                    continue;
                }
                
                String symbol = fetchedData.getOrDefault("symbol", "").toString();
                if (symbol == null || symbol.isEmpty()) {
                    continue;
                }
                
                // 업비트에 이미 있는 종목이면 건너뜀
                if (upbitSymbols.contains(symbol)) {
                    continue;
                }
                
                // marketCode는 KRW-symbol 형식
                String marketCode = "KRW-" + symbol;
                
                // 같은 배치 내 중복 방지
                if (processedCoinoneMarketCodes.contains(marketCode)) {
                    continue;
                }
                
                processedCoinoneMarketCodes.add(marketCode);
                
                // DB에 이미 있는 코인인지 확인
                Optional<Coin> existingCoin = coinRepository.findByMarketCode(marketCode);
                if (existingCoin.isPresent()) {
                    // DB에 이미 있는 코인 -> API 응답에 포함되어 있으므로 활성화
                    Coin coin = existingCoin.get();
                    if ("COINONE".equals(coin.getExchange()) && !coin.getIsActive()) {
                        coin.setIsActive(true);
                        coinoneCoinsToActivate.add(coin);
                    }
                    continue;
                }
                
                String koreanName = fetchedData.getOrDefault("koreanName", "").toString();
                String englishName = fetchedData.getOrDefault("englishName", "").toString();
                String imgUrl = "/data/image/" + symbol + ".png";
                
                Coin coin = Coin.builder()
                    .symbol(symbol)
                    .quoteCurrency("KRW")
                    .marketCode(marketCode)
                    .koreanName(koreanName)
                    .englishName(englishName)
                    .imgUrl(imgUrl)
                    .exchange("COINONE")
                    .isActive(true)
                    .build();
                
                newCoinoneCoinList.add(coin);
            }
            
            // 3-3. 신규 코인 저장 및 기존 코인 활성화 (업비트 + 코인원)
            int newCount = 0;
            int activatedCount = 0;
            
            // 신규 코인 저장
            if (!newCoinList.isEmpty()) {
                coinRepository.saveAll(newCoinList);
                newCount += newCoinList.size();
            }
            if (!newCoinoneCoinList.isEmpty()) {
                coinRepository.saveAll(newCoinoneCoinList);
                newCount += newCoinoneCoinList.size();
            }
            
            // 기존 코인 활성화 (DB에 있지만 비활성화 상태였던 코인)
            if (!coinsToActivate.isEmpty()) {
                coinRepository.saveAll(coinsToActivate);
                activatedCount += coinsToActivate.size();
            }
            if (!coinoneCoinsToActivate.isEmpty()) {
                coinRepository.saveAll(coinoneCoinsToActivate);
                activatedCount += coinoneCoinsToActivate.size();
            }

            // 4. DB 에 있지만 API 응답에 없는 코인 → is_active = false로 업데이트 (거래소별로 분리)
            // 해당 거래소의 데이터가 실제로 패치되었을 때만 비활성화 로직 실행
            List<Coin> coinsToDeactivate = new ArrayList<>();
            
            // 4-1. 업비트 코인 비활성화 (업비트 데이터가 패치되었을 때만 실행)
            if (!fetchedActiveUpbitMarketCodes.isEmpty()) {
                for(String dbSavedMarketCode : dbSavedUpbitMarketCodes){
                    if(!fetchedActiveUpbitMarketCodes.contains(dbSavedMarketCode)){ // API 응답에 없는 코인 -> 비활성화
                        Coin coin = coinRepository.findByMarketCode(dbSavedMarketCode)
                            .orElseThrow(() -> new CustomException(ErrorCode.COIN_NOT_FOUND));
                        // 업비트 코인인지 확인
                        if("UPBIT".equals(coin.getExchange())){
                            coin.setIsActive(false);
                            coinsToDeactivate.add(coin);
                        }
                    }
                }
            }
            
            // 4-2. 코인원 코인 비활성화 (코인원 데이터가 패치되었을 때만 실행)
            if (!fetchedActiveCoinoneMarketCodes.isEmpty()) {
                for(String dbSavedMarketCode : dbSavedCoinoneMarketCodes){
                    if(!fetchedActiveCoinoneMarketCodes.contains(dbSavedMarketCode)){ // API 응답에 없는 코인 -> 비활성화
                        Coin coin = coinRepository.findByMarketCode(dbSavedMarketCode)
                            .orElseThrow(() -> new CustomException(ErrorCode.COIN_NOT_FOUND));
                        // 코인원 코인인지 확인
                        if("COINONE".equals(coin.getExchange())){
                            coin.setIsActive(false);
                            coinsToDeactivate.add(coin);
                        }
                    }
                }
            }
            
            // 4-3. update 반영
            int deactivatedCount = 0;
            if (!coinsToDeactivate.isEmpty()) {
                coinRepository.saveAll(coinsToDeactivate);
                deactivatedCount = coinsToDeactivate.size();
            }

            // 신규 코인 로그 (중복 제거)
            if (!newCoinList.isEmpty()) {
                Set<String> loggedUpbitCoins = new HashSet<>();
                for(Coin coin : newCoinList){
                    String key = coin.getMarketCode();
                    if (!loggedUpbitCoins.contains(key)) {
                        log.info("신규 업비트 코인: {} ({})", coin.getKoreanName(), coin.getMarketCode());
                        loggedUpbitCoins.add(key);
                    }
                }
            }
            
            if (!newCoinoneCoinList.isEmpty()) {
                Set<String> loggedCoinoneCoins = new HashSet<>();
                for(Coin coin : newCoinoneCoinList){
                    String key = coin.getMarketCode();
                    if (!loggedCoinoneCoins.contains(key)) {
                        log.info("신규 코인원 코인: {} ({})", coin.getKoreanName(), coin.getMarketCode());
                        loggedCoinoneCoins.add(key);
                    }
                }
            }

            if (!coinsToDeactivate.isEmpty()) {
                Set<String> loggedDeactivatedCoins = new HashSet<>();
                for(Coin coin : coinsToDeactivate){
                    String key = coin.getMarketCode();
                    if (!loggedDeactivatedCoins.contains(key)) {
                        log.info("비활성화 코인: {} ({})", coin.getKoreanName(), coin.getMarketCode());
                        loggedDeactivatedCoins.add(key);
                    }
                }
            }
            
            log.info("코인 목록 저장 완료: 신규 추가 {}개 (업비트: {}, 코인원: {}), 활성화 {}개, 비활성화 {}개, 업비트 총 {}개, 코인원 총 {}개",
                newCount, newCoinList.size(), newCoinoneCoinList.size(), activatedCount, deactivatedCount, 
                fetchedActiveUpbitMarketCodes.size(), fetchedActiveCoinoneMarketCodes.size());
            
            // 5. 저장 결과 반환 
            Map<String, Object> result = new HashMap<>();
            result.put("new", newCount);
            result.put("deactivated", deactivatedCount);
            result.put("total", fetchedActiveUpbitMarketCodes.size() + fetchedActiveCoinoneMarketCodes.size());
            
            return result;
        } catch (CustomException e) {
            log.error("saveAllCoinList - {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("saveAllCoinList - 데이터베이스 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, "코인 목록 저장 중 데이터베이스 오류가 발생했습니다: " + e.getMessage());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("saveAllCoinList - 데이터 검증 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.BAD_REQUEST, "코인 데이터 처리 중 오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("saveAllCoinList - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, "코인 목록 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private String convertMarketCodeFormat(String marketCode) {
        if (marketCode == null || !marketCode.contains("/")) {
            return marketCode;
        }
        
        String[] parts = marketCode.split("/");
        if (parts.length == 2) {
            return parts[1] + "-" + parts[0];
        }
        
        return marketCode;
    }
}
