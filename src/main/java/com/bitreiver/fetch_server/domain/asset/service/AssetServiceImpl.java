package com.bitreiver.fetch_server.domain.asset.service;

import com.bitreiver.fetch_server.domain.asset.entity.Asset;
import com.bitreiver.fetch_server.domain.asset.repository.AssetRepository;
import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.service.ExchangeCredentialService;
import com.bitreiver.fetch_server.domain.coinone.service.CoinoneService;
import com.bitreiver.fetch_server.domain.upbit.service.UpbitService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {
    
    private final AssetRepository assetRepository;
    private final CoinRepository coinRepository;
    private final UpbitService upbitService;
    private final CoinoneService coinoneService;
    private final ExchangeCredentialService exchangeCredentialService;
    
    private Integer getCoinId(String symbol, String tradeBySymbol, String exchange) {
        String marketCode;
        // 거래소별 marketCode 형식이 다름
        if ("COINONE".equals(exchange)) {
            // 코인원: KRW-symbol 형식 (거래통화-symbol)
            marketCode = tradeBySymbol + "-" + symbol;
        } else {
            // 업비트 등: symbol/quoteCurrency 형식
            marketCode = symbol + "/" + tradeBySymbol;
        }
        
        // 먼저 marketCode로 찾기 (가장 정확)
        Optional<Coin> coinByMarket = coinRepository.findByMarketCode(marketCode);
        if (coinByMarket.isPresent()) {
            return coinByMarket.get().getId();
        }
        
        // marketCode로 못 찾으면 symbol + quoteCurrency + exchange로 찾기
        Optional<Coin> coinBySymbol = coinRepository.findBySymbolAndQuoteCurrencyAndExchange(
            symbol, tradeBySymbol, exchange);
        if (coinBySymbol.isPresent()) {
            return coinBySymbol.get().getId();
        }
        
        log.warn("getCoinId - coin_id를 찾을 수 없습니다: symbol={}, trade_by_symbol={}, market_code={}, exchange={}", 
            symbol, tradeBySymbol, marketCode, exchange);
        return null;
    }
    
    private Asset convertAccountToAsset(Map<String, Object> account, String exchange) {
        String currency = account.getOrDefault("currency", "").toString();
        String unitCurrency = account.getOrDefault("unit_currency", "KRW").toString();
        BigDecimal balance = new BigDecimal(account.getOrDefault("balance", "0").toString());
        BigDecimal locked = new BigDecimal(account.getOrDefault("locked", "0").toString());
        BigDecimal avgBuyPrice = new BigDecimal(account.getOrDefault("avg_buy_price", "0").toString());
        Boolean avgBuyPriceModified = Boolean.parseBoolean(account.getOrDefault("avg_buy_price_modified", "false").toString());
        
        Integer coinId = getCoinId(currency, unitCurrency, exchange);
        
        return Asset.builder()
            .coinId(coinId)
            .symbol(currency)
            .tradeBySymbol(unitCurrency)
            .quantity(balance)
            .lockedQuantity(locked)
            .avgBuyPrice(avgBuyPrice)
            .avgBuyPriceModified(avgBuyPriceModified)
            .build();
    }
    
    @Override
    @Transactional
    public Map<String, Object> syncUpbitAssets(UUID userId) {
        try {
            ExchangeCredentialResponse credentials = exchangeCredentialService
                .getCredentials(userId, (short) ExchangeType.UPBIT.getCode())
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "Upbit 자격증명을 찾을 수 없습니다"));
            
            if (credentials.getAccessKey() == null || credentials.getSecretKey() == null) {
                throw new CustomException(ErrorCode.CREDENTIALS_DECRYPTION_FAILED, 
                    "자격증명 복호화에 실패했습니다");
            }
            
            List<Map<String, Object>> accounts = upbitService.fetchAccounts(
                credentials.getAccessKey(),
                credentials.getSecretKey()
            ).block();
            
            if (accounts == null || accounts.isEmpty()) {
                log.warn("syncUpbitAssets - Upbit 계정 잔고가 비어있습니다: user_id={}", userId);
                List<Asset> allAssets = assetRepository.findByUserIdAndExchangeCode(
                    userId, (short) ExchangeType.UPBIT.getCode());
                assetRepository.deleteAll(allAssets);
                
                Map<String, Object> result = new HashMap<>();
                result.put("saved_count", 0);
                result.put("deleted_count", allAssets.size());
                result.put("assets", new ArrayList<>());
                return result;
            }
            
            List<Asset> assets = new ArrayList<>();
            Set<String> symbolTradeByPairs = new HashSet<>();
            
            for (Map<String, Object> account : accounts) {
                // 잔고가 0인 코인은 제외
                BigDecimal balance = new BigDecimal(account.getOrDefault("balance", "0").toString());
                if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                Asset asset = convertAccountToAsset(account, "UPBIT");
                assets.add(asset);
                symbolTradeByPairs.add(asset.getSymbol() + ":" + asset.getTradeBySymbol());
            }
            
            List<Asset> savedAssets = new ArrayList<>();
            for (Asset asset : assets) {
                Optional<Asset> existing = assetRepository.findByUserIdAndExchangeCodeAndSymbolAndTradeBySymbol(
                    userId,
                    (short) ExchangeType.UPBIT.getCode(),
                    asset.getSymbol(),
                    asset.getTradeBySymbol()
                );
                
                if (existing.isPresent()) {
                    Asset existingAsset = existing.get();
                    existingAsset.setQuantity(asset.getQuantity());
                    existingAsset.setLockedQuantity(asset.getLockedQuantity());
                    existingAsset.setAvgBuyPrice(asset.getAvgBuyPrice());
                    existingAsset.setAvgBuyPriceModified(asset.getAvgBuyPriceModified());
                    existingAsset.setCoinId(asset.getCoinId());
                    existingAsset.setUpdatedAt(LocalDateTime.now());
                    savedAssets.add(assetRepository.save(existingAsset));
                } else {
                    asset.setUserId(userId);
                    asset.setExchangeCode((short) ExchangeType.UPBIT.getCode());
                    asset.setCreatedAt(LocalDateTime.now());
                    asset.setUpdatedAt(LocalDateTime.now());
                    savedAssets.add(assetRepository.save(asset));
                }
            }
            
            List<Asset> allAssets = assetRepository.findByUserIdAndExchangeCode(
                userId, (short) ExchangeType.UPBIT.getCode());
            
            int deletedCount = 0;
            for (Asset asset : allAssets) {
                String pair = asset.getSymbol() + ":" + asset.getTradeBySymbol();
                if (!symbolTradeByPairs.contains(pair)) {
                    assetRepository.delete(asset);
                    deletedCount++;
                }
            }
            
            log.info("syncUpbitAssets - Upbit 자산 동기화 완료: user_id={}, saved={}, deleted={}", 
                userId, savedAssets.size(), deletedCount);
            
            List<Map<String, Object>> assetList = savedAssets.stream()
                .map(asset -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", asset.getId());
                    map.put("symbol", asset.getSymbol());
                    map.put("trade_by_symbol", asset.getTradeBySymbol());
                    map.put("quantity", asset.getQuantity().doubleValue());
                    map.put("locked_quantity", asset.getLockedQuantity().doubleValue());
                    map.put("avg_buy_price", asset.getAvgBuyPrice().doubleValue());
                    return map;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("saved_count", savedAssets.size());
            result.put("deleted_count", deletedCount);
            result.put("assets", assetList);
            
            return result;
        } catch (CustomException e) {
            log.error("syncUpbitAssets - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("syncUpbitAssets - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자산 동기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public Map<String, Object> syncCoinoneAssets(UUID userId) {
        try {
            ExchangeCredentialResponse credentials = exchangeCredentialService
                .getCredentials(userId, (short) ExchangeType.COINONE.getCode())
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "코인원 자격증명을 찾을 수 없습니다"));
            
            if (credentials.getAccessKey() == null || credentials.getSecretKey() == null) {
                throw new CustomException(ErrorCode.CREDENTIALS_DECRYPTION_FAILED, 
                    "자격증명 복호화에 실패했습니다");
            }
            
            List<Map<String, Object>> accounts = coinoneService.fetchAccounts(
                credentials.getAccessKey(),
                credentials.getSecretKey()
            ).block();
            
            if (accounts == null || accounts.isEmpty()) {
                log.warn("syncCoinoneAssets - 코인원 계정 잔고가 비어있습니다: user_id={}", userId);
                List<Asset> allAssets = assetRepository.findByUserIdAndExchangeCode(
                    userId, (short) ExchangeType.COINONE.getCode());
                assetRepository.deleteAll(allAssets);
                
                Map<String, Object> result = new HashMap<>();
                result.put("saved_count", 0);
                result.put("deleted_count", allAssets.size());
                result.put("assets", new ArrayList<>());
                return result;
            }
            
            List<Asset> assets = new ArrayList<>();
            Set<String> symbolTradeByPairs = new HashSet<>();
            
            for (Map<String, Object> account : accounts) {
                // 잔고가 0인 코인은 제외
                BigDecimal balance = new BigDecimal(account.getOrDefault("balance", "0").toString());
                if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                Asset asset = convertAccountToAsset(account, "COINONE");
                assets.add(asset);
                symbolTradeByPairs.add(asset.getSymbol() + ":" + asset.getTradeBySymbol());
            }
            
            List<Asset> savedAssets = new ArrayList<>();
            for (Asset asset : assets) {
                Optional<Asset> existing = assetRepository.findByUserIdAndExchangeCodeAndSymbolAndTradeBySymbol(
                    userId,
                    (short) ExchangeType.COINONE.getCode(),
                    asset.getSymbol(),
                    asset.getTradeBySymbol()
                );
                
                if (existing.isPresent()) {
                    Asset existingAsset = existing.get();
                    existingAsset.setQuantity(asset.getQuantity());
                    existingAsset.setLockedQuantity(asset.getLockedQuantity());
                    existingAsset.setAvgBuyPrice(asset.getAvgBuyPrice());
                    existingAsset.setAvgBuyPriceModified(asset.getAvgBuyPriceModified());
                    existingAsset.setCoinId(asset.getCoinId());
                    existingAsset.setUpdatedAt(LocalDateTime.now());
                    savedAssets.add(assetRepository.save(existingAsset));
                } else {
                    asset.setUserId(userId);
                    asset.setExchangeCode((short) ExchangeType.COINONE.getCode());
                    asset.setCreatedAt(LocalDateTime.now());
                    asset.setUpdatedAt(LocalDateTime.now());
                    savedAssets.add(assetRepository.save(asset));
                }
            }
            
            List<Asset> allAssets = assetRepository.findByUserIdAndExchangeCode(
                userId, (short) ExchangeType.COINONE.getCode());
            
            int deletedCount = 0;
            for (Asset asset : allAssets) {
                String pair = asset.getSymbol() + ":" + asset.getTradeBySymbol();
                if (!symbolTradeByPairs.contains(pair)) {
                    assetRepository.delete(asset);
                    deletedCount++;
                }
            }
            
            log.info("syncCoinoneAssets - 코인원 자산 동기화 완료: user_id={}, saved={}, deleted={}", 
                userId, savedAssets.size(), deletedCount);
            
            List<Map<String, Object>> assetList = savedAssets.stream()
                .map(asset -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", asset.getId());
                    map.put("symbol", asset.getSymbol());
                    map.put("trade_by_symbol", asset.getTradeBySymbol());
                    map.put("quantity", asset.getQuantity().doubleValue());
                    map.put("locked_quantity", asset.getLockedQuantity().doubleValue());
                    map.put("avg_buy_price", asset.getAvgBuyPrice().doubleValue());
                    return map;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("saved_count", savedAssets.size());
            result.put("deleted_count", deletedCount);
            result.put("assets", assetList);
            
            return result;
        } catch (CustomException e) {
            log.error("syncCoinoneAssets - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("syncCoinoneAssets - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자산 동기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public Map<String, Object> syncAllExchangeAssets(UUID userId) {
        try {
            log.info("syncAllExchangeAssets - 시작: user_id={}", userId);
            
            // 사용자의 모든 거래소 자격증명 조회
            List<ExchangeCredentialResponse> allCredentials = exchangeCredentialService.getAllCredentials(userId);
            
            log.info("syncAllExchangeAssets - 조회된 자격증명 수: user_id={}, count={}", userId, 
                allCredentials != null ? allCredentials.size() : 0);
            
            if (allCredentials == null || allCredentials.isEmpty()) {
                log.warn("syncAllExchangeAssets - 연동된 거래소가 없습니다: user_id={}", userId);
                Map<String, Object> result = new HashMap<>();
                result.put("total_saved_count", 0);
                result.put("total_deleted_count", 0);
                result.put("exchanges", new ArrayList<>());
                return result;
            }
            
            Map<String, Object> totalResult = new HashMap<>();
            int totalSavedCount = 0;
            int totalDeletedCount = 0;
            List<Map<String, Object>> exchangeResults = new ArrayList<>();
            
            for (ExchangeCredentialResponse credential : allCredentials) {
                Short exchangeProvider = credential.getExchangeProvider();
                ExchangeType exchangeType = ExchangeType.fromCode(exchangeProvider.intValue());
                
                log.info("syncAllExchangeAssets - 거래소 자산 동기화 시작: user_id={}, exchange={}", 
                    userId, exchangeType.getName());
                
                try {
                    Map<String, Object> exchangeResult;
                    
                    switch (exchangeType) {
                        case UPBIT:
                            log.info("syncAllExchangeAssets - 업비트 자산 동기화 시작: user_id={}", userId);
                            exchangeResult = syncUpbitAssets(userId);
                            break;
                        case COINONE:
                            log.info("syncAllExchangeAssets - 코인원 자산 동기화 시작: user_id={}", userId);
                            exchangeResult = syncCoinoneAssets(userId);
                            break;
                        default:
                            log.warn("syncAllExchangeAssets - 지원하지 않는 거래소: exchange={}, user_id={}", 
                                exchangeType.getName(), userId);
                            continue;
                    }
                    
                    int savedCount = (Integer) exchangeResult.getOrDefault("saved_count", 0);
                    int deletedCount = (Integer) exchangeResult.getOrDefault("deleted_count", 0);
                    
                    totalSavedCount += savedCount;
                    totalDeletedCount += deletedCount;
                    
                    Map<String, Object> exchangeInfo = new HashMap<>();
                    exchangeInfo.put("exchange", exchangeType.name()); // 영문 이름 사용 (UPBIT, COINONE 등) - updateTradingHistory API에서 사용
                    exchangeInfo.put("exchange_name", exchangeType.getName()); // 한글 이름도 포함 (표시용)
                    exchangeInfo.put("exchange_code", exchangeProvider);
                    exchangeInfo.put("saved_count", savedCount);
                    exchangeInfo.put("deleted_count", deletedCount);
                    exchangeInfo.put("assets", exchangeResult.get("assets"));
                    exchangeResults.add(exchangeInfo);
                    
                    log.info("syncAllExchangeAssets - {} 자산 동기화 완료: user_id={}, saved={}, deleted={}", 
                        exchangeType.getName(), userId, savedCount, deletedCount);
                    
                } catch (Exception e) {
                    log.error("syncAllExchangeAssets - {} 자산 동기화 실패: user_id={}, error={}", 
                        exchangeType.getName(), userId, e.getMessage(), e);
                    // 개별 거래소 실패해도 다른 거래소는 계속 진행
                }
            }
            
            totalResult.put("total_saved_count", totalSavedCount);
            totalResult.put("total_deleted_count", totalDeletedCount);
            totalResult.put("exchanges", exchangeResults);
            
            log.info("syncAllExchangeAssets - 전체 자산 동기화 완료: user_id={}, total_saved={}, total_deleted={}, exchanges={}", 
                userId, totalSavedCount, totalDeletedCount, exchangeResults.size());
            
            return totalResult;
        } catch (Exception e) {
            log.error("syncAllExchangeAssets - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자산 동기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
