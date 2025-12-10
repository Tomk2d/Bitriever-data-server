package com.bitreiver.fetch_server.domain.trading.service;

import com.bitreiver.fetch_server.domain.trading.dto.TradingHistoryListResponse;
import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TradingHistoryService {
    List<Map<String, Object>> getTradingHistories(UUID userId, String exchangeProviderStr, LocalDateTime startTime);
    List<TradingHistory> processTradingHistories(UUID userId, String exchangeProviderStr, List<Map<String, Object>> tradingHisties);
    List<TradingHistory> saveTradingHistories(List<TradingHistory> tradingHistories);
    TradingHistoryListResponse getAllTradingHistoriesByUserFormatted(UUID userId);
    Map<String, Object> getAllTradingHistoriesByUserFormattedAsMap(UUID userId);
}
