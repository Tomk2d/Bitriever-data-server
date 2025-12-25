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
public class BinanceTakerLongShortRatioResponse {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("buyVol")
    private String buyVol;
    
    @JsonProperty("sellVol")
    private String sellVol;
    
    @JsonProperty("buySellRatio")
    private String buySellRatio;
    
    @JsonProperty("timestamp")
    private Long timestamp;
}

