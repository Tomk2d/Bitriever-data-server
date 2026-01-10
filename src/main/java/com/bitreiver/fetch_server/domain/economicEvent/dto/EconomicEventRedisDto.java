package com.bitreiver.fetch_server.domain.economicEvent.dto;

import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEventValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicEventRedisDto {
    private Long id;
    private String uniqueName;
    private LocalDate eventDate;
    private String title;
    private String subtitleText;
    private String countryType;
    private Boolean excludeFromAll;
    private EconomicEventValueRedisDto economicEventValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static EconomicEventRedisDto from(EconomicEvent event) {
        return EconomicEventRedisDto.builder()
            .id(event.getId())
            .uniqueName(event.getUniqueName())
            .eventDate(event.getEventDate())
            .title(event.getTitle())
            .subtitleText(event.getSubtitleText())
            .countryType(event.getCountryType())
            .excludeFromAll(event.getExcludeFromAll())
            .economicEventValue(EconomicEventValueRedisDto.from(event.getEconomicEventValue()))
            .createdAt(event.getCreatedAt())
            .updatedAt(event.getUpdatedAt())
            .build();
    }
    
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EconomicEventValueRedisDto {
        private Long id;
        private String ric;
        private String unit;
        private String unitPrefix;
        private BigDecimal actual;
        private BigDecimal forecast;
        private BigDecimal actualForecastDiff;
        private BigDecimal historical;
        private LocalTime time;
        private String preAnnouncementWording;
        
        public static EconomicEventValueRedisDto from(EconomicEventValue value) {
            if (value == null) {
                return null;
            }
            
            return EconomicEventValueRedisDto.builder()
                .id(value.getId())
                .ric(value.getRic())
                .unit(value.getUnit())
                .unitPrefix(value.getUnitPrefix())
                .actual(value.getActual())
                .forecast(value.getForecast())
                .actualForecastDiff(value.getActualForecastDiff())
                .historical(value.getHistorical())
                .time(value.getTime())
                .preAnnouncementWording(value.getPreAnnouncementWording())
                .build();
        }
    }
}
