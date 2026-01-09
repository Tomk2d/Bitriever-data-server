package com.bitreiver.fetch_server.infra.tossInvest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossInvestCalendarResponse {
    @JsonProperty("result")
    private Result result;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("events")
        private List<Event> events;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        @JsonProperty("id")
        private EventId id;
        
        @JsonProperty("view")
        private View view;
        
        @JsonProperty("date")
        private String date;
        
        @JsonProperty("excludeFromAll")
        private Boolean excludeFromAll;
        
        @JsonProperty("stockEarnings")
        private Object stockEarnings;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventId {
        @JsonProperty("uniqueName")
        private String uniqueName;
        
        @JsonProperty("group")
        private String group;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class View {
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("subtitle")
        private Subtitle subtitle;
        
        @JsonProperty("landingOption")
        private LandingOption landingOption;
        
        @JsonProperty("economicIndicatorValue")
        private EconomicIndicatorValue economicIndicatorValue;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subtitle {
        @JsonProperty("text")
        private String text;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LandingOption {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("cta")
        private Object cta;
        
        @JsonProperty("landingUrl")
        private String landingUrl;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EconomicIndicatorValue {
        @JsonProperty("ric")
        private String ric;
        
        @JsonProperty("unit")
        private String unit;
        
        @JsonProperty("unitPrefix")
        private String unitPrefix;
        
        @JsonProperty("actual")
        private BigDecimal actual;
        
        @JsonProperty("forecast")
        private BigDecimal forecast;
        
        @JsonProperty("actualForecastDiff")
        private BigDecimal actualForecastDiff;
        
        @JsonProperty("historical")
        private BigDecimal historical;
        
        @JsonProperty("time")
        private String time;
        
        @JsonProperty("countryType")
        private String countryType;
        
        @JsonProperty("preAnnouncementWording")
        private String preAnnouncementWording;
    }
}
