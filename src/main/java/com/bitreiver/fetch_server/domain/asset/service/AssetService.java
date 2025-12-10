package com.bitreiver.fetch_server.domain.asset.service;

import java.util.Map;
import java.util.UUID;

public interface AssetService {
    Map<String, Object> syncUpbitAssets(UUID userId);
}
