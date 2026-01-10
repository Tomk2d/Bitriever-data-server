package com.bitreiver.fetch_server.domain.economicEvent.service;

import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEventValue;
import com.bitreiver.fetch_server.domain.economicEvent.repository.EconomicEventRepository;
import com.bitreiver.fetch_server.domain.economicEvent.repository.EconomicEventValueRepository;
import com.bitreiver.fetch_server.infra.tossInvest.TossInvestCalendarClient;
import com.bitreiver.fetch_server.infra.tossInvest.dto.TossInvestCalendarResponse;
import com.bitreiver.fetch_server.domain.economicEvent.dto.EconomicEventRedisDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EconomicEventServiceImpl implements EconomicEventService {
    private final TossInvestCalendarClient tossInvestCalendarClient;
    private final EconomicEventRepository economicEventRepository;
    private final EconomicEventValueRepository economicEventValueRepository;
    private final RedisCacheService redisCacheService;
    
    private static final String EVENT_GROUP_ECONOMIC = "ECONOMIC";
    private static final String START_YEAR_MONTH = "2026-01";
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String REDIS_KEY_PREFIX = "economic-events:upcoming:";
    private static final long TTL_SECONDS = 86400;   // 1일

    @Override
    @Transactional
    public int fetchAndSaveMonthlyData(String yearMonth) {
        try{
            TossInvestCalendarResponse response = tossInvestCalendarClient
                .getMonthlyCalendar(yearMonth)
                .block();

                if (response == null || response.getResult() == null || response.getResult().getEvents() == null) {
                    log.warn("응답이 null 입니다.", yearMonth);
                    return 0;
                }

            List<TossInvestCalendarResponse.Event> events = response.getResult().getEvents();

            List<TossInvestCalendarResponse.Event> economicEvents = events.stream()
                .filter(event -> event.getId() != null 
                    && EVENT_GROUP_ECONOMIC.equals(event.getId().getGroup()))
                .toList();

            int savedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for(TossInvestCalendarResponse.Event event : economicEvents){
                String uniqueName = event.getId().getUniqueName();
                
                // 기존 데이터 조회
                Optional<EconomicEvent> existingEventOpt = economicEventRepository.findByUniqueName(uniqueName);
                
                if (existingEventOpt.isPresent()) {
                    // 기존 데이터 업데이트 (변경 감지 활용)
                    EconomicEvent existingEvent = existingEventOpt.get();
                    updateEconomicEvent(existingEvent, event);
                    economicEventRepository.save(existingEvent); // 변경 감지로 UPDATE 실행
                    updatedCount++;
                } else {
                    // 신규 데이터 저장
                    EconomicEvent economicEvent = buildEconomicEvent(event);
                    
                    // EconomicEventValue 생성 (economicIndicatorValue가 있는 경우)
                    if (event.getView() != null && event.getView().getEconomicIndicatorValue() != null) {
                        EconomicEventValue economicEventValue = buildEconomicEventValue(
                            event.getView().getEconomicIndicatorValue(), economicEvent);
                        economicEvent.setEconomicEventValue(economicEventValue);
                    }
                    
                    economicEventRepository.save(economicEvent);
                    savedCount++;
                }
            }

            log.info("경제 지표 데이터 저장 완료: {} - 신규={}, 업데이트={}, 건너뜀={}, 총={}", 
                yearMonth, savedCount, updatedCount, skippedCount, economicEvents.size());
            
            return savedCount + updatedCount;
        } catch (Exception e) {
            log.error("경제 지표 데이터 수집 실패: {}", yearMonth, e);
            throw new RuntimeException("경제 지표 데이터 수집 실패: " + yearMonth, e);
        }
    }

    @Override
    public int fetchAndSaveAllMonthlyData() {

        YearMonth startYearMonth = YearMonth.parse(START_YEAR_MONTH, YEAR_MONTH_FORMATTER);
        YearMonth currentYearMonth = YearMonth.now();
        YearMonth endYearMonth = currentYearMonth.plusMonths(2);

        int totalSaved = 0;
        YearMonth current = startYearMonth;

        while(!current.isAfter(endYearMonth)) {
            String yearMonth = current.format(YEAR_MONTH_FORMATTER);

            try{
                int saved = fetchAndSaveMonthlyData(yearMonth);
                totalSaved += saved;

                if(!current.isAfter(endYearMonth)) {
                    Thread.sleep(1000);
                }
            }catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("스레드 인터럽트 발생", e);
                break;
            } catch (Exception e) {
                log.error("월별 데이터 수집 실패: {}", yearMonth, e);
            }
            
            current = current.plusMonths(1);
        }
        
        log.info("전체 월별 경제 지표 데이터 수집 완료: 총 저장={}", totalSaved);
        return totalSaved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EconomicEvent> getUpcomingEvents(int limit) {
        LocalDate today = LocalDate.now();
        return economicEventRepository.findUpcomingEvents(today, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public void cacheUpcomingEvents(int limit) {
        try {
            List<EconomicEvent> events = getUpcomingEvents(limit);
            
            List<EconomicEventRedisDto> dtoList = events.stream()
                .map(EconomicEventRedisDto::from)
                .collect(Collectors.toList());
            
            String redisKey = REDIS_KEY_PREFIX + "top" + limit;
            redisCacheService.set(redisKey, dtoList, TTL_SECONDS);
            
        } catch (Exception e) {
            log.error("다가오는 경제 지표 이벤트 Redis 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("다가오는 경제 지표 이벤트 Redis 저장 실패", e);
        }
    }

    private EconomicEvent buildEconomicEvent(TossInvestCalendarResponse.Event event) {
        TossInvestCalendarResponse.View view = event.getView();
        TossInvestCalendarResponse.EconomicIndicatorValue indicatorValue = 
            view != null ? view.getEconomicIndicatorValue() : null;
        
        String countryType = indicatorValue != null && indicatorValue.getCountryType() != null
            ? indicatorValue.getCountryType()
            : "us"; // 기본값
        
        // subtitleText: view.subtitle.text + " " + landingOption.text
        String subtitleText = buildSubtitleText(view);
        
        return EconomicEvent.builder()
            .uniqueName(event.getId().getUniqueName())
            .eventDate(LocalDate.parse(event.getDate()))
            .title(view != null ? view.getTitle() : "")
            .subtitleText(subtitleText)
            .countryType(countryType)
            .excludeFromAll(event.getExcludeFromAll() != null ? event.getExcludeFromAll() : false)
            .build();
    }
    
    private String buildSubtitleText(TossInvestCalendarResponse.View view) {
        if (view == null) {
            return null;
        }
        
        String subtitleText = null;
        if (view.getSubtitle() != null && view.getSubtitle().getText() != null) {
            subtitleText = view.getSubtitle().getText();
        }
        
        String landingOptionText = null;
        if (view.getLandingOption() != null && view.getLandingOption().getText() != null) {
            landingOptionText = view.getLandingOption().getText();
        }
        
        // subtitleText + " " + landingOptionText 형식으로 합치기
        if (subtitleText != null && landingOptionText != null) {
            return subtitleText + " " + landingOptionText;
        } else if (subtitleText != null) {
            return subtitleText;
        } else if (landingOptionText != null) {
            return landingOptionText;
        }
        
        return null;
    }
    
    /**
     * 기존 EconomicEvent를 API 응답 데이터로 업데이트
     * JPA 변경 감지를 활용하여 자동으로 UPDATE 쿼리 실행
     */
    private void updateEconomicEvent(EconomicEvent existingEvent, TossInvestCalendarResponse.Event event) {
        TossInvestCalendarResponse.View view = event.getView();
        TossInvestCalendarResponse.EconomicIndicatorValue indicatorValue = 
            view != null ? view.getEconomicIndicatorValue() : null;
        
        // EconomicEvent 필드 업데이트
        if (view != null) {
            if (view.getTitle() != null) {
                existingEvent.setTitle(view.getTitle());
            }
            
            // subtitleText 업데이트
            String subtitleText = buildSubtitleText(view);
            existingEvent.setSubtitleText(subtitleText);
        }
        
        // countryType 업데이트
        if (indicatorValue != null && indicatorValue.getCountryType() != null) {
            existingEvent.setCountryType(indicatorValue.getCountryType());
        }
        
        if (event.getExcludeFromAll() != null) {
            existingEvent.setExcludeFromAll(event.getExcludeFromAll());
        }
        
        // EconomicEventValue 업데이트
        if (view != null && indicatorValue != null) {
            if (existingEvent.getEconomicEventValue() != null) {
                // 기존 EconomicEventValue 업데이트
                updateEconomicEventValue(existingEvent.getEconomicEventValue(), indicatorValue);
            } else {
                // EconomicEventValue가 없으면 새로 생성
                EconomicEventValue economicEventValue = buildEconomicEventValue(
                    indicatorValue, existingEvent);
                existingEvent.setEconomicEventValue(economicEventValue);
            }
        } else if (existingEvent.getEconomicEventValue() != null && indicatorValue == null) {
            // API 응답에 indicatorValue가 없으면 기존 EconomicEventValue 삭제
            existingEvent.setEconomicEventValue(null);
        }
    }

    /**
     * 기존 EconomicEventValue를 API 응답 데이터로 업데이트
     */
    private void updateEconomicEventValue(EconomicEventValue existingValue, 
                                          TossInvestCalendarResponse.EconomicIndicatorValue indicatorValue) {
        if (indicatorValue.getRic() != null) {
            existingValue.setRic(indicatorValue.getRic());
        }
        if (indicatorValue.getUnit() != null) {
            existingValue.setUnit(indicatorValue.getUnit());
        }
        if (indicatorValue.getUnitPrefix() != null) {
            existingValue.setUnitPrefix(indicatorValue.getUnitPrefix());
        }
        
        // actual, forecast, historical 등은 null일 수도 있으므로 항상 업데이트
        existingValue.setActual(indicatorValue.getActual());
        existingValue.setForecast(indicatorValue.getForecast());
        existingValue.setActualForecastDiff(indicatorValue.getActualForecastDiff());
        existingValue.setHistorical(indicatorValue.getHistorical());
        
        // time 파싱 및 업데이트
        if (indicatorValue.getTime() != null && !indicatorValue.getTime().isEmpty()) {
            try {
                existingValue.setTime(LocalTime.parse(indicatorValue.getTime()));
            } catch (Exception e) {
                log.warn("시간 파싱 실패: {}", indicatorValue.getTime());
            }
        } else {
            existingValue.setTime(null);
        }
        
        if (indicatorValue.getPreAnnouncementWording() != null) {
            existingValue.setPreAnnouncementWording(indicatorValue.getPreAnnouncementWording());
        }
    }
    
    private EconomicEventValue buildEconomicEventValue(
            TossInvestCalendarResponse.EconomicIndicatorValue indicatorValue,
            EconomicEvent economicEvent) {
        
        LocalTime time = null;
        if (indicatorValue.getTime() != null && !indicatorValue.getTime().isEmpty()) {
            try {
                time = LocalTime.parse(indicatorValue.getTime());
            } catch (Exception e) {
                log.warn("시간 파싱 실패: {}", indicatorValue.getTime());
            }
        }
        
        return EconomicEventValue.builder()
            .economicEvent(economicEvent)
            .ric(indicatorValue.getRic())
            .unit(indicatorValue.getUnit())
            .unitPrefix(indicatorValue.getUnitPrefix())
            .actual(indicatorValue.getActual())
            .forecast(indicatorValue.getForecast())
            .actualForecastDiff(indicatorValue.getActualForecastDiff())
            .historical(indicatorValue.getHistorical())
            .time(time)
            .preAnnouncementWording(indicatorValue.getPreAnnouncementWording())
            .build();
    }
}
