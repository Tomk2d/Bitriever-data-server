package com.bitreiver.fetch_server.domain.economicEvent;

import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEventValue;
import com.bitreiver.fetch_server.domain.economicEvent.repository.EconomicEventRepository;
import com.bitreiver.fetch_server.domain.economicEvent.repository.EconomicEventValueRepository;
import com.bitreiver.fetch_server.domain.economicEvent.service.EconomicEventService;
import com.bitreiver.fetch_server.infra.tossInvest.TossInvestCalendarClient;
import com.bitreiver.fetch_server.infra.tossInvest.dto.TossInvestCalendarResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class EconomicEventServiceTest {

    @MockBean
    private TossInvestCalendarClient tossInvestCalendarClient;

    @Autowired
    private EconomicEventRepository economicEventRepository;

    @Autowired
    private EconomicEventValueRepository economicEventValueRepository;

    @Autowired
    private EconomicEventService economicEventService;

    @BeforeEach
    void setUp() {
        // 테스트 전 데이터 정리
        economicEventValueRepository.deleteAll();
        economicEventRepository.deleteAll();
        reset(tossInvestCalendarClient);
    }

    private TossInvestCalendarResponse createMockResponse() {
        TossInvestCalendarResponse response = new TossInvestCalendarResponse();
        TossInvestCalendarResponse.Result result = new TossInvestCalendarResponse.Result();
        List<TossInvestCalendarResponse.Event> events = new ArrayList<>();

        // ECONOMIC 그룹 이벤트 1
        TossInvestCalendarResponse.Event economicEvent1 = createEconomicEvent(
            "20260106_ISM제조업구매관리자지수발표_ECONOMIC",
            "ISM 제조업 구매관리자지수 발표",
            "미국 제조업 경기 상황을 빠르게 파악할 수 있어요.",
            "2026-01-06",
            "USPMI=ECI",
            "Index Point",
            "",
            new BigDecimal("47.90"),
            new BigDecimal("48.20"),
            "00:00:00",
            "us",
            "지난달"
        );
        events.add(economicEvent1);

        // ECONOMIC 그룹 이벤트 2
        TossInvestCalendarResponse.Event economicEvent2 = createEconomicEvent(
            "20260107_비농업부문고용변화량(ADP)발표_ECONOMIC",
            "비농업부문 고용변화량(ADP) 발표",
            "",
            "2026-01-07",
            "USADP=ECI",
            "DecimalOneCount",
            "만 건",
            new BigDecimal("4.1"),
            new BigDecimal("-3.2"),
            "22:15:00",
            "us",
            "지난달"
        );
        events.add(economicEvent2);

        // HOLIDAY 그룹 이벤트 (필터링되어야 함)
        TossInvestCalendarResponse.Event holidayEvent = new TossInvestCalendarResponse.Event();
        TossInvestCalendarResponse.EventId holidayId = new TossInvestCalendarResponse.EventId();
        holidayId.setUniqueName("20260101_신정_HOLIDAY_KR");
        holidayId.setGroup("HOLIDAY");
        holidayEvent.setId(holidayId);
        holidayEvent.setDate("2026-01-01");
        events.add(holidayEvent);

        result.setEvents(events);
        response.setResult(result);
        return response;
    }

    private TossInvestCalendarResponse.Event createEconomicEvent(
            String uniqueName, String title, String subtitleText, String date,
            String ric, String unit, String unitPrefix, BigDecimal actual,
            BigDecimal historical, String time, String countryType, String preAnnouncementWording) {
        
        TossInvestCalendarResponse.Event event = new TossInvestCalendarResponse.Event();
        
        // ID 설정
        TossInvestCalendarResponse.EventId id = new TossInvestCalendarResponse.EventId();
        id.setUniqueName(uniqueName);
        id.setGroup("ECONOMIC");
        event.setId(id);
        
        // View 설정
        TossInvestCalendarResponse.View view = new TossInvestCalendarResponse.View();
        view.setTitle(title);
        
        // Subtitle 설정
        TossInvestCalendarResponse.Subtitle subtitle = new TossInvestCalendarResponse.Subtitle();
        subtitle.setText(subtitleText);
        view.setSubtitle(subtitle);
        
        // EconomicIndicatorValue 설정
        TossInvestCalendarResponse.EconomicIndicatorValue indicatorValue = 
            new TossInvestCalendarResponse.EconomicIndicatorValue();
        indicatorValue.setRic(ric);
        indicatorValue.setUnit(unit);
        indicatorValue.setUnitPrefix(unitPrefix);
        indicatorValue.setActual(actual);
        indicatorValue.setForecast(null);
        indicatorValue.setActualForecastDiff(null);
        indicatorValue.setHistorical(historical);
        indicatorValue.setTime(time);
        indicatorValue.setCountryType(countryType);
        indicatorValue.setPreAnnouncementWording(preAnnouncementWording);
        view.setEconomicIndicatorValue(indicatorValue);
        
        event.setView(view);
        event.setDate(date);
        event.setExcludeFromAll(false);
        
        return event;
    }

    @Test
    @DisplayName("월별 데이터 수집 및 저장 테스트 - ECONOMIC 그룹만 필터링")
    void fetchAndSaveMonthlyData_success() {
        System.out.println("\n=== 월별 데이터 수집 테스트 시작 ===\n");

        // Mock 설정
        when(tossInvestCalendarClient.getMonthlyCalendar("2026-01"))
            .thenReturn(Mono.just(createMockResponse()));

        // 테스트 실행
        int savedCount = economicEventService.fetchAndSaveMonthlyData("2026-01");

        // 검증
        assertEquals(2, savedCount, "ECONOMIC 그룹 이벤트 2개가 저장되어야 합니다.");
        
        // DB에서 확인
        List<EconomicEvent> savedEvents = economicEventRepository.findAll();
        assertEquals(2, savedEvents.size(), "저장된 이벤트는 2개여야 합니다.");
        
        // 첫 번째 이벤트 검증
        EconomicEvent event1 = savedEvents.stream()
            .filter(e -> e.getUniqueName().contains("ISM제조업"))
            .findFirst()
            .orElseThrow();
        
        assertEquals("ISM 제조업 구매관리자지수 발표", event1.getTitle());
        assertEquals("미국 제조업 경기 상황을 빠르게 파악할 수 있어요.", event1.getSubtitleText());
        assertEquals("us", event1.getCountryType());
        assertNotNull(event1.getEconomicEventValue(), "EconomicEventValue가 저장되어야 합니다.");
        
        EconomicEventValue value1 = event1.getEconomicEventValue();
        assertEquals("USPMI=ECI", value1.getRic());
        assertEquals("Index Point", value1.getUnit());
        // BigDecimal 비교는 compareTo() 사용 (scale 무시)
        assertEquals(0, new BigDecimal("47.90").compareTo(value1.getActual()), 
            "actual 값이 일치해야 합니다: " + value1.getActual());
        assertEquals(0, new BigDecimal("48.20").compareTo(value1.getHistorical()), 
            "historical 값이 일치해야 합니다: " + value1.getHistorical());
        
        // Mock 호출 검증
        verify(tossInvestCalendarClient, times(1)).getMonthlyCalendar("2026-01");
        
        System.out.println("✓ 저장된 이벤트 수: " + savedCount);
        System.out.println("✓ 첫 번째 이벤트: " + event1.getTitle());
        System.out.println("✓ ECONOMIC 그룹만 필터링 확인");
        System.out.println("\n=== 월별 데이터 수집 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("중복 체크 테스트 - 동일한 uniqueName은 저장하지 않음")
    void fetchAndSaveMonthlyData_duplicateCheck() {
        System.out.println("\n=== 중복 체크 테스트 시작 ===\n");

        // 첫 번째 저장
        when(tossInvestCalendarClient.getMonthlyCalendar("2026-01"))
            .thenReturn(Mono.just(createMockResponse()));
        
        int firstSaved = economicEventService.fetchAndSaveMonthlyData("2026-01");
        assertEquals(2, firstSaved);

        // 두 번째 저장 (중복)
        int secondSaved = economicEventService.fetchAndSaveMonthlyData("2026-01");
        assertEquals(0, secondSaved, "중복된 데이터는 저장되지 않아야 합니다.");

        // DB에서 확인 (여전히 2개만 있어야 함)
        List<EconomicEvent> allEvents = economicEventRepository.findAll();
        assertEquals(2, allEvents.size(), "중복 저장되지 않아야 합니다.");

        System.out.println("✓ 첫 번째 저장: " + firstSaved);
        System.out.println("✓ 두 번째 저장 (중복): " + secondSaved);
        System.out.println("✓ 중복 체크 정상 동작 확인");
        System.out.println("\n=== 중복 체크 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("응답이 null인 경우 처리 테스트")
    void fetchAndSaveMonthlyData_nullResponse() {
        System.out.println("\n=== null 응답 처리 테스트 시작 ===\n");

        // Mock 설정 - null 응답 (Mono.fromCallable로 null 반환)
        when(tossInvestCalendarClient.getMonthlyCalendar("2026-01"))
            .thenReturn(Mono.fromCallable(() -> null));

        // 테스트 실행
        int savedCount = economicEventService.fetchAndSaveMonthlyData("2026-01");

        // 검증
        assertEquals(0, savedCount, "null 응답인 경우 0개가 저장되어야 합니다.");
        
        List<EconomicEvent> allEvents = economicEventRepository.findAll();
        assertEquals(0, allEvents.size(), "저장된 이벤트가 없어야 합니다.");

        System.out.println("✓ null 응답 처리 정상 동작");
        System.out.println("\n=== null 응답 처리 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("economicIndicatorValue가 null인 경우 처리 테스트")
    void fetchAndSaveMonthlyData_nullIndicatorValue() {
        System.out.println("\n=== null indicatorValue 처리 테스트 시작 ===\n");

        // economicIndicatorValue가 null인 이벤트 생성
        TossInvestCalendarResponse response = new TossInvestCalendarResponse();
        TossInvestCalendarResponse.Result result = new TossInvestCalendarResponse.Result();
        List<TossInvestCalendarResponse.Event> events = new ArrayList<>();

        TossInvestCalendarResponse.Event event = new TossInvestCalendarResponse.Event();
        TossInvestCalendarResponse.EventId id = new TossInvestCalendarResponse.EventId();
        id.setUniqueName("20260101_TEST_ECONOMIC");
        id.setGroup("ECONOMIC");
        event.setId(id);

        TossInvestCalendarResponse.View view = new TossInvestCalendarResponse.View();
        view.setTitle("테스트 이벤트");
        view.setEconomicIndicatorValue(null); // null
        event.setView(view);
        event.setDate("2026-01-01");
        events.add(event);

        result.setEvents(events);
        response.setResult(result);

        // Mock 설정
        when(tossInvestCalendarClient.getMonthlyCalendar("2026-01"))
            .thenReturn(Mono.just(response));

        // 테스트 실행
        int savedCount = economicEventService.fetchAndSaveMonthlyData("2026-01");

        // 검증
        assertEquals(1, savedCount, "이벤트는 저장되어야 합니다.");
        
        EconomicEvent savedEvent = economicEventRepository.findAll().get(0);
        assertNotNull(savedEvent);
        assertEquals("테스트 이벤트", savedEvent.getTitle());
        assertNull(savedEvent.getEconomicEventValue(), "economicIndicatorValue가 null이면 EconomicEventValue는 저장되지 않아야 합니다.");

        System.out.println("✓ EconomicEvent는 저장됨");
        System.out.println("✓ EconomicEventValue는 저장되지 않음 (null)");
        System.out.println("\n=== null indicatorValue 처리 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("API 호출 실패 시 예외 처리 테스트")
    void fetchAndSaveMonthlyData_apiError() {
        System.out.println("\n=== API 에러 처리 테스트 시작 ===\n");

        // Mock 설정 - 예외 발생
        when(tossInvestCalendarClient.getMonthlyCalendar("2026-01"))
            .thenReturn(Mono.error(new RuntimeException("API 호출 실패")));

        // 테스트 실행 및 검증
        assertThrows(RuntimeException.class, () -> {
            economicEventService.fetchAndSaveMonthlyData("2026-01");
        }, "API 호출 실패 시 RuntimeException이 발생해야 합니다.");

        System.out.println("✓ API 에러 처리 정상 동작");
        System.out.println("\n=== API 에러 처리 테스트 완료 ===\n");
    }
}
