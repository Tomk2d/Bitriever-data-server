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
            // 1. 패치 데이터에서 활성화 코인만 추리기(marketState=ACTIVE, exchange=UPBIT)
            Set<String> fetchedActiveMarketCodes = fetchedDataList.stream()
                .filter(data -> "UPBIT".equals(data.getOrDefault("exchange", "").toString()))
                .map(data -> {
                    String pair = data.getOrDefault("pair", "").toString();
                    return convertMarketCodeFormat(pair);
                })
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());
            
            // 2. DB에 있는 모든 UPBIT 코인 조회
            List<Coin> allUpbitCoins = coinRepository.findByExchange("UPBIT");
            // 2-1. DB에 있는 모든 UPBIT 코인의 marketCode Set 생성
            Set<String> dbSavedMarketCodes = allUpbitCoins.stream()
                .filter(coin -> coin.getIsActive() == true)
                .map(Coin::getMarketCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());

            // 3. 신규 저장할 코인 리스트 생성 (API에 있지만 DB에 없는 코인)
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
                
                // 3-3. DB에 없는 코인만 생성 -> 신규 저장 리스트에 추가
                if (!dbSavedMarketCodes.contains(marketCode)) {
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
                        .exchange("upbit")
                        .isActive(true)
                        .build();
                    
                    newCoinList.add(coin);
                }
            }
            
            // 3-2. 신규 코인 저장
            int newCount = 0;
            if (!newCoinList.isEmpty()) {
                coinRepository.saveAll(newCoinList);
                newCount = newCoinList.size();
            }

            // 4. DB 에 있지만 API 응답에 없는 코인 → is_active = false로 업데이트
            // 4-1. (update 항목)DB 에 있지만 API 응답에 없는 코인 리스트 생성
            List<Coin> coinsToDeactivate = new ArrayList<>();
            
            for(String dbSavedMarketCode : dbSavedMarketCodes){
                if(!fetchedActiveMarketCodes.contains(dbSavedMarketCode)){ // API 응답에 없는 코인 -> 비활성화
                    Coin coin = coinRepository.findByMarketCode(dbSavedMarketCode)
                        .orElseThrow(() -> new CustomException(ErrorCode.COIN_NOT_FOUND));
                    coin.setIsActive(false);
                    coinsToDeactivate.add(coin);
                }
            }
            
            // 4-2. update 반영
            int deactivatedCount = 0;
            if (!coinsToDeactivate.isEmpty()) {
                coinRepository.saveAll(coinsToDeactivate);
                deactivatedCount = coinsToDeactivate.size();
            }

            for(Coin coin : newCoinList){
                log.info("신규 코인: {}", coin.getKoreanName());
            }

            for(Coin coin : coinsToDeactivate){
                log.info("비활성화 코인: {}", coin.getKoreanName());
            }
            
            log.info("코인 목록 저장 완료: 신규 추가 {}개, 비활성화 {}개, 총 {}개",
                newCount, deactivatedCount, fetchedActiveMarketCodes.size());
            
            // 5. 저장 결과 반환 
            Map<String, Object> result = new HashMap<>();
            result.put("new", newCount);
            result.put("deactivated", deactivatedCount);
            result.put("total", fetchedActiveMarketCodes.size());
            
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
