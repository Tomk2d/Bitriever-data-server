package com.bitreiver.fetch_server.domain.asset.service;

import java.util.Map;
import java.util.UUID;

public interface AssetService {
    Map<String, Object> syncUpbitAssets(UUID userId);
    Map<String, Object> syncCoinoneAssets(UUID userId);
    Map<String, Object> syncBithumbAssets(UUID userId);
    Map<String, Object> syncAllExchangeAssets(UUID userId);
}
