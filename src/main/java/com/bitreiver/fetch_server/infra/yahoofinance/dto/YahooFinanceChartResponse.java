package com.bitreiver.fetch_server.infra.yahoofinance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooFinanceChartResponse {
    @JsonProperty("chart")
    private Chart chart;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {
        @JsonProperty("result")
        private List<Result> result;
        
        @JsonProperty("error")
        private Object error;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("meta")
        private Meta meta;
        
        @JsonProperty("timestamp")
        private List<Long> timestamp;
        
        @JsonProperty("indicators")
        private Indicators indicators;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        @JsonProperty("currency")
        private String currency;
        
        @JsonProperty("symbol")
        private String symbol;
        
        @JsonProperty("regularMarketPrice")
        private Double regularMarketPrice;
        
        @JsonProperty("chartPreviousClose")
        private Double chartPreviousClose;
        
        @JsonProperty("regularMarketTime")
        private Long regularMarketTime;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Indicators {
        @JsonProperty("quote")
        private List<Quote> quote;
        
        @JsonProperty("adjclose")
        private List<AdjClose> adjclose;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        @JsonProperty("open")
        private List<Double> open;
        
        @JsonProperty("high")
        private List<Double> high;
        
        @JsonProperty("low")
        private List<Double> low;
        
        @JsonProperty("close")
        private List<Double> close;
        
        @JsonProperty("volume")
        private List<Long> volume;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdjClose {
        @JsonProperty("adjclose")
        private List<Double> adjclose;
    }
}
