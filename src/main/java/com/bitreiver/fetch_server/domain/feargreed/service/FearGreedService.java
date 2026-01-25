package com.bitreiver.fetch_server.domain.feargreed.service;

import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiResponse;

import java.time.LocalDate;
import java.util.Map;

public interface FearGreedService {
    FearGreedApiResponse getByDate(LocalDate date);
    Map<String, Object> fetchAndSaveAllHistory();
    
    /**
     * 최근 일주일 데이터를 가져와서 DB에 증분 저장
     * 이미 존재하는 날짜는 스킵, 새로운 날짜만 저장
     * @return 처리 결과 (total_fetched, saved, skipped)
     */
    Map<String, Object> fetchRecentDataAndSaveToDb();

    void fetchRecentDataAndSaveToRedis();
    void fetchYesterdayDataAndSaveToRedis();
    void saveAllHistoryToRedis();
}
