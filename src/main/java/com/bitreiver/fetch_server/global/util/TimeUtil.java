package com.bitreiver.fetch_server.global.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TimeUtil {
    private static final ZoneId KOREA_TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC_TIMEZONE = ZoneId.of("UTC");
    private static final DateTimeFormatter ISO8601_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    public static LocalDateTime getCurrentKoreaTime() {
        return LocalDateTime.now(KOREA_TIMEZONE);
    }
    
    public static String formatIso8601(LocalDateTime dateTime) {
        ZonedDateTime zonedDateTime = dateTime.atZone(KOREA_TIMEZONE);
        return zonedDateTime.format(ISO8601_FORMATTER);
    }
    
    public static LocalDateTime parseIso8601(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return null;
        }
        // Z를 +00:00로 변환
        String normalized = isoString.replace("Z", "+00:00");
        return ZonedDateTime.parse(normalized).withZoneSameInstant(KOREA_TIMEZONE).toLocalDateTime();
    }
    
    public static List<String[]> getAllTradingTimeRanges(LocalDateTime startDate, LocalDateTime currentTime) {
        List<String[]> timeRanges = new ArrayList<>();
        
        if (startDate.isAfter(currentTime) || startDate.isEqual(currentTime)) {
            return timeRanges;
        }
        
        LocalDateTime currentStart = startDate;
        int maxDays = 7;
        
        while (currentStart.isBefore(currentTime)) {
            LocalDateTime currentEnd = currentStart.plusDays(maxDays);
            if (currentEnd.isAfter(currentTime)) {
                currentEnd = currentTime;
            }
            
            // 1초 빼기
            currentEnd = currentEnd.minusSeconds(1);
            
            timeRanges.add(new String[]{
                formatIso8601(currentStart),
                formatIso8601(currentEnd)
            });
            
            currentStart = currentStart.plusDays(maxDays);
        }
        
        return timeRanges;
    }
    
    /**
     * 코인원 API용 시간 범위 분할 (최대 90일씩)
     * 
     * @param startDate 시작 날짜
     * @param currentTime 현재 시간
     * @return 시간 범위 배열 리스트 (각 요소는 [from_ts, to_ts] 형태의 UTC millisecond 타임스탬프)
     */
    public static List<Long[]> getCoinoneTimeRanges(LocalDateTime startDate, LocalDateTime currentTime) {
        List<Long[]> timeRanges = new ArrayList<>();
        
        // startDate가 currentTime 이후이거나 같으면, startDate를 1시간 전으로 조정하여 최소 범위 보장
        if (startDate.isAfter(currentTime) || startDate.isEqual(currentTime)) {
            startDate = currentTime.minusHours(1);
        }
        
        // startDate와 currentTime 차이가 1분 미만이면, startDate를 1시간 전으로 조정
        if (java.time.Duration.between(startDate, currentTime).toMinutes() < 1) {
            startDate = currentTime.minusHours(1);
        }
        
        LocalDateTime currentStart = startDate;
        int maxDays = 90; // 코인원 API 제약: 최대 90일
        
        while (currentStart.isBefore(currentTime)) {
            LocalDateTime currentEnd = currentStart.plusDays(maxDays);
            if (currentEnd.isAfter(currentTime)) {
                currentEnd = currentTime;
            }
            
            // 1초 빼기 (범위 끝 제외)
            currentEnd = currentEnd.minusSeconds(1);
            
            // UTC로 변환하여 millisecond 타임스탬프로 변환
            long fromTs = currentStart.atZone(KOREA_TIMEZONE)
                .withZoneSameInstant(UTC_TIMEZONE)
                .toInstant()
                .toEpochMilli();
            
            long toTs = currentEnd.atZone(KOREA_TIMEZONE)
                .withZoneSameInstant(UTC_TIMEZONE)
                .toInstant()
                .toEpochMilli();
            
            timeRanges.add(new Long[]{fromTs, toTs});
            
            currentStart = currentStart.plusDays(maxDays);
        }
        
        return timeRanges;
    }
    
    /**
     * LocalDateTime을 UTC millisecond 타임스탬프로 변환
     * 
     * @param dateTime 한국 시간 기준 LocalDateTime
     * @return UTC millisecond 타임스탬프
     */
    public static long toUtcMilliseconds(LocalDateTime dateTime) {
        return dateTime.atZone(KOREA_TIMEZONE)
            .withZoneSameInstant(UTC_TIMEZONE)
            .toInstant()
            .toEpochMilli();
    }
    
    /**
     * UTC millisecond 타임스탬프를 LocalDateTime으로 변환 (한국 시간 기준)
     * 
     * @param timestamp UTC millisecond 타임스탬프
     * @return 한국 시간 기준 LocalDateTime
     */
    public static LocalDateTime fromUtcMilliseconds(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
            .atZone(UTC_TIMEZONE)
            .withZoneSameInstant(KOREA_TIMEZONE)
            .toLocalDateTime();
    }
}

