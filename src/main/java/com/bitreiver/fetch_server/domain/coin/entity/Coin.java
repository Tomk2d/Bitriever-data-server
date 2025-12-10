package com.bitreiver.fetch_server.domain.coin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "coins", indexes = {
    @Index(name = "idx_market_code", columnList = "market_code"),
    @Index(name = "idx_symbol", columnList = "symbol")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coin {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "quote_currency", nullable = false, length = 10)
    private String quoteCurrency;
    
    @Column(name = "market_code", unique = true, length = 20)
    private String marketCode;
    
    @Column(name = "korean_name", length = 50)
    private String koreanName;
    
    @Column(name = "english_name", length = 50)
    private String englishName;
    
    @Column(name = "img_url", length = 1000)
    private String imgUrl;
    
    @Column(name = "exchange", nullable = false, length = 20)
    @Builder.Default
    private String exchange = "upbit";
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}

