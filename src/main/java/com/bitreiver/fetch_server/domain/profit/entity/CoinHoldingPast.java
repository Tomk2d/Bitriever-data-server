package com.bitreiver.fetch_server.domain.profit.entity;

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
@Table(name = "coin_holdings_past",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coin_holdings_past_user_coin_exchange",
            columnNames = {"user_id", "coin_id", "exchange_code"}
        )
    },
    indexes = {
        @Index(name = "idx_holdings_user_id", columnList = "user_id"),
        @Index(name = "idx_holdings_coin_id", columnList = "coin_id"),
        @Index(name = "idx_holdings_exchange_code", columnList = "exchange_code")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinHoldingPast {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;
    
    @Column(name = "coin_id")
    private Integer coinId;
    
    @Column(name = "exchange_code", nullable = false)
    private Short exchangeCode;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "avg_buy_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal avgBuyPrice;
    
    @Column(name = "remaining_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal remainingQuantity;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

