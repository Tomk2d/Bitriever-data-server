package com.bitreiver.fetch_server.infra.tossInvest;

import com.bitreiver.fetch_server.infra.tossInvest.dto.TossInvestCalendarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TossInvestCalendarClient {
    private final WebClient tossInvestWebClient;
    
    public TossInvestCalendarClient(@Qualifier("tossInvestWebClient") WebClient tossInvestWebClient) {
        this.tossInvestWebClient = tossInvestWebClient;
    }

    /**
     * 토스인베스트 캘린더 API 호출
     * 
     * @param yearMonth 월별 형식 (예: "2026-01")
     * @return 캘린더 응답
     */
    public Mono<TossInvestCalendarResponse> getMonthlyCalendar(String yearMonth) {
        String uri = "/api/v4/calendar/monthly/" + yearMonth;
                
        return tossInvestWebClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .retrieve()
            .bodyToMono(TossInvestCalendarResponse.class)
            .doOnError(error -> log.error("토스인베스트 캘린더 API 호출 실패 - yearMonth: {}, error: {}", 
                yearMonth, error.getMessage()));
    }
}
