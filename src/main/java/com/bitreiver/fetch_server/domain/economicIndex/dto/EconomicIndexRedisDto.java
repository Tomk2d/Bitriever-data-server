package com.bitreiver.fetch_server.domain.economicIndex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicIndexRedisDto {
    private LocalDateTime dateTime;
    private String dateTimeString;
    private BigDecimal price;
    private BigDecimal previousClose;
    private BigDecimal changeAmount; // 전일 대비 등락 금액
    private BigDecimal changeRate;   // 전일 대비 등락률 (%)
}