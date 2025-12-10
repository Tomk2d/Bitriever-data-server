package com.bitreiver.fetch_server.domain.coin.service;

import java.util.List;
import java.util.Map;

public interface CoinService {
    Map<String, Object> saveAllCoinList(List<Map<String, Object>> fetchedDataList);
}
