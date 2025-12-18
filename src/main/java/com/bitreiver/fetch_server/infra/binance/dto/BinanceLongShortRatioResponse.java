package com.bitreiver.fetch_server.infra.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceLongShortRatioResponse {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("longShortRatio")
    private String longShortRatio;
    
    @JsonProperty("longAccount")
    private String longAccount;
    
    @JsonProperty("shortAccount")
    private String shortAccount;
    
    @JsonProperty("timestamp")
    private Long timestamp;
}

