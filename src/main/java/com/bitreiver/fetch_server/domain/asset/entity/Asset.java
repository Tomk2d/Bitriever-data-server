package com.bitreiver.fetch_server.domain.asset.entity;

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
@Table(name = "assets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_assets_user_exchange_symbol",
            columnNames = {"user_id", "exchange_code", "symbol", "trade_by_symbol"}
        )
    },
    indexes = {
        @Index(name = "idx_assets_user_id", columnList = "user_id"),
        @Index(name = "idx_assets_exchange_code", columnList = "exchange_code"),
        @Index(name = "idx_assets_coin_id", columnList = "coin_id"),
        @Index(name = "idx_assets_user_exchange", columnList = "user_id, exchange_code"),
        @Index(name = "idx_assets_user_symbol", columnList = "user_id, symbol"),
        @Index(name = "idx_assets_exchange_symbol", columnList = "exchange_code, symbol")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;
    
    @Column(name = "exchange_code", nullable = false)
    private Short exchangeCode;
    
    @Column(name = "coin_id")
    private Integer coinId;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "trade_by_symbol", nullable = false, length = 10)
    private String tradeBySymbol;
    
    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "locked_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal lockedQuantity;
    
    @Column(name = "avg_buy_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal avgBuyPrice;
    
    @Column(name = "avg_buy_price_modified", nullable = false)
    private Boolean avgBuyPriceModified;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

