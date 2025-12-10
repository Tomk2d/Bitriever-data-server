package com.bitreiver.fetch_server.domain.trading.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trading_histories", 
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_exchange_trade_uuid",
            columnNames = {"user_id", "exchange_code", "trade_uuid"}
        )
    },
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_coin_id", columnList = "coin_id"),
        @Index(name = "idx_trade_time", columnList = "trade_time"),
        @Index(name = "idx_exchange_code", columnList = "exchange_code"),
        @Index(name = "idx_user_trade_time", columnList = "user_id, trade_time")
    })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;
    
    @Column(name = "coin_id", nullable = false)
    private Integer coinId;
    
    @Column(name = "exchange_code", nullable = false)
    private Short exchangeCode;
    
    @Column(name = "trade_uuid", nullable = false, length = 100)
    private String tradeUuid;
    
    @Column(name = "trade_type", nullable = false)
    private Short tradeType;
    
    @Column(name = "price", nullable = false, precision = 20, scale = 8)
    private BigDecimal price;
    
    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "total_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal totalPrice;
    
    @Column(name = "fee", precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
    
    @Column(name = "trade_time", nullable = false)
    private LocalDateTime tradeTime;
    
    @Setter
    @Column(name = "profit_loss_rate", precision = 5, scale = 2)
    private BigDecimal profitLossRate;
    
    @Setter
    @Column(name = "avg_buy_price", precision = 20, scale = 8)
    private BigDecimal avgBuyPrice;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

