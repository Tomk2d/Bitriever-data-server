package com.bitreiver.fetch_server.domain.longshort.service;

import java.util.Map;

public interface LongShortRatioService {
    
    /**
     * 모든 활성 코인에 대한 Binance Long/Short Ratio 조회
     * 
     * @param period 기간 ("5m","15m","30m","1h","2h","4h","6h","12h","1d")
     * @param limit 제한 (기본값 30, 최대 500)
     * @return 심볼별 Long/Short Ratio 데이터 맵과 미지원 심볼 리스트
     */
    Map<String, Object> fetchAll(String period, Long limit);
    
    /**
     * 모든 활성 코인에 대한 Binance Long/Short Ratio 조회 및 Redis 저장
     * 
     * @param period 기간 ("5m","15m","30m","1h","2h","4h","6h","12h","1d")
     * @param limit 제한 (기본값 30, 최대 500)
     */
    void fetchAllAndSaveToRedis(String period, Long limit);
}

