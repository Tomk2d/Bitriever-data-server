package com.bitreiver.fetch_server.global.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TimeUtil {
    private static final ZoneId KOREA_TIMEZONE = ZoneId.of("Asia/Seoul");
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
}

