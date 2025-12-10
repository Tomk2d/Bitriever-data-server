package com.bitreiver.fetch_server.domain.profit.service;

import java.util.Map;
import java.util.UUID;

public interface TradingProfitService {
    Map<String, Object> calculateAndUpdateProfitLoss(UUID userId, Integer exchangeCode, Boolean isInitial);
}
