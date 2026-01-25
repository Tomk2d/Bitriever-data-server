package com.bitreiver.fetch_server.domain.price.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coin_prices_day", indexes = {
    @Index(name = "idx_coin_prices_day_coin_id", columnList = "coin_id"),
    @Index(name = "idx_coin_prices_day_coin_id_date_utc", columnList = "coin_id, candle_date_time_utc"),
    @Index(name = "idx_coin_prices_day_coin_id_date_kst", columnList = "coin_id, candle_date_time_kst"),
    @Index(name = "idx_coin_prices_day_market_date", columnList = "market_code, candle_date_time_utc"),
    @Index(name = "idx_coin_prices_day_market", columnList = "market_code"),
    @Index(name = "idx_coin_prices_day_date_utc", columnList = "candle_date_time_utc"),
    @Index(name = "idx_coin_prices_day_date_kst", columnList = "candle_date_time_kst")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_coin_prices_day_market_date", columnNames = {"market_code", "candle_date_time_utc"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinPriceDay {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "coin_id", nullable = false)
    private Integer coinId;
    
    @Column(name = "market_code", nullable = false, length = 20)
    private String marketCode;
    
    @Column(name = "candle_date_time_utc", nullable = false)
    private LocalDateTime candleDateTimeUtc;
    
    @Column(name = "candle_date_time_kst", nullable = false)
    private LocalDateTime candleDateTimeKst;
    
    @Column(name = "opening_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal openingPrice;
    
    @Column(name = "high_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal highPrice;
    
    @Column(name = "low_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal lowPrice;
    
    @Column(name = "trade_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal tradePrice;
    
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;
    
    @Column(name = "candle_acc_trade_price", nullable = false, precision = 30, scale = 8)
    private BigDecimal candleAccTradePrice;
    
    @Column(name = "candle_acc_trade_volume", nullable = false, precision = 30, scale = 8)
    private BigDecimal candleAccTradeVolume;
    
    @Column(name = "prev_closing_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal prevClosingPrice;
    
    @Column(name = "change_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal changePrice;
    
    @Column(name = "change_rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal changeRate;
    
    @Column(name = "converted_trade_price", precision = 20, scale = 8)
    private BigDecimal convertedTradePrice;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
