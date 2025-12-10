package com.bitreiver.fetch_server.domain.feargreed.service;

import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiResponse;

import java.time.LocalDate;
import java.util.Map;

public interface FearGreedService {
    FearGreedApiResponse getByDate(LocalDate date);
    void fetchRecentData();
    Map<String, Object> fetchAndSaveAllHistory();
}
