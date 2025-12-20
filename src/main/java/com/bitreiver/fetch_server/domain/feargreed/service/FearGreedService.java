package com.bitreiver.fetch_server.domain.feargreed.service;

import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiResponse;

import java.time.LocalDate;
import java.util.Map;

public interface FearGreedService {
    FearGreedApiResponse getByDate(LocalDate date);
    Map<String, Object> fetchAndSaveAllHistory();

    void fetchRecentDataAndSaveToRedis();
    void fetchYesterdayDataAndSaveToRedis();
    void saveAllHistoryToRedis();
}
