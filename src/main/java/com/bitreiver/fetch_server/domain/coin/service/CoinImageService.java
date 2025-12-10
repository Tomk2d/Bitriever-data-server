package com.bitreiver.fetch_server.domain.coin.service;

import java.util.List;
import java.util.Map;

public interface CoinImageService {
    int downloadCoinImages(List<Map<String, Object>> coinList);
}
