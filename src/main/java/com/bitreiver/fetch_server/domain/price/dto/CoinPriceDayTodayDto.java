package com.bitreiver.fetch_server.domain.price.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 당일 일봉 데이터 (Redis 저장용).
 * 확정되지 않은 당일 봉은 DB가 아닌 Redis에만 저장하며, app-server에서 ticker로 high/low/trade_price를 갱신한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinPriceDayTodayDto {

    public static final String REDIS_KEY_PREFIX = "coin:daily:today:";
    /** TTL: 48시간 (자정 넘겨도 당일 키 유지) */
    public static final long REDIS_TTL_SECONDS = 48 * 3600L;

    private Integer coinId;
    private String exchange;
    private String marketCode;
    private LocalDateTime candleDateTimeUtc;
    private LocalDateTime candleDateTimeKst;
    private BigDecimal openingPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal tradePrice;
    private Long timestamp;
    private BigDecimal candleAccTradePrice;
    private BigDecimal candleAccTradeVolume;
    private BigDecimal prevClosingPrice;
    private BigDecimal changePrice;
    private BigDecimal changeRate;
    private BigDecimal convertedTradePrice;

    public static String redisKey(String exchange, String marketCode) {
        return REDIS_KEY_PREFIX + exchange + ":" + marketCode;
    }
}
