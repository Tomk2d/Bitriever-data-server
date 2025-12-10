package com.bitreiver.fetch_server.domain.feargreed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FearGreedApiTestResponse {
    
    @JsonProperty("success")
    private Integer success;
    
    @JsonProperty("data")
    private FearGreedData data;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FearGreedData {
        @JsonProperty("datasets")
        private List<Dataset> datasets;
        
        @JsonProperty("labels")
        private List<String> labels;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dataset {
        @JsonProperty("data")
        private List<Integer> data;
        
        @JsonProperty("label")
        private String label;
        
        @JsonProperty("backgroundColor")
        private String backgroundColor;
        
        @JsonProperty("borderColor")
        private String borderColor;
        
        @JsonProperty("fill")
        private Boolean fill;
    }
}

