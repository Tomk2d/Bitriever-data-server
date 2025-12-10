package com.bitreiver.fetch_server.domain.asset.service;

import com.bitreiver.fetch_server.domain.asset.entity.Asset;
import com.bitreiver.fetch_server.domain.asset.repository.AssetRepository;
import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.service.ExchangeCredentialService;
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
    private final ExchangeCredentialService exchangeCredentialService;
    
    private Integer getCoinId(String symbol, String tradeBySymbol) {
        String marketCode = symbol + "/" + tradeBySymbol;
        
        Optional<Coin> coinByMarket = coinRepository.findByMarketCode(marketCode);
        if (coinByMarket.isPresent()) {
            return coinByMarket.get().getId();
        }
        
        Optional<Coin> coinBySymbol = coinRepository.findBySymbolAndQuoteCurrency(symbol, tradeBySymbol);
        if (coinBySymbol.isPresent()) {
            return coinBySymbol.get().getId();
        }
        
        log.warn("getCoinId - coin_id를 찾을 수 없습니다: symbol={}, trade_by_symbol={}", symbol, tradeBySymbol);
        return null;
    }
    
    private Asset convertUpbitAccountToAsset(Map<String, Object> account) {
        String currency = account.getOrDefault("currency", "").toString();
        String unitCurrency = account.getOrDefault("unit_currency", "KRW").toString();
        BigDecimal balance = new BigDecimal(account.getOrDefault("balance", "0").toString());
        BigDecimal locked = new BigDecimal(account.getOrDefault("locked", "0").toString());
        BigDecimal avgBuyPrice = new BigDecimal(account.getOrDefault("avg_buy_price", "0").toString());
        Boolean avgBuyPriceModified = Boolean.parseBoolean(account.getOrDefault("avg_buy_price_modified", "false").toString());
        
        Integer coinId = getCoinId(currency, unitCurrency);
        
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
                Asset asset = convertUpbitAccountToAsset(account);
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
}
