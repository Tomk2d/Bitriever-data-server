package com.bitreiver.fetch_server.domain.longshort.runner;

import com.bitreiver.fetch_server.domain.longshort.service.LongShortRatioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * fetch-server 기동 시 롱숏 비율 데이터를 최초 1회 조회하여 Redis에 적재합니다.
 * 배치 스케줄이 돌기 전까지 app-server에서 빈 데이터를 반환하지 않도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class LongShortRatioStartupRunner implements ApplicationRunner {

    private static final String[] PERIODS = {"1h", "4h", "12h", "1d"};
    private static final long LIMIT = 30L;

    private final LongShortRatioService longShortRatioService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("롱숏 비율 기동 시 최초 1회 조회 시작");
        new Thread(() -> {
            try {
                for (String period : PERIODS) {
                    try {
                        longShortRatioService.fetchAllAndSaveToRedis(period, LIMIT);
                        log.info("롱숏 비율 기동 시 조회 완료 - period: {}", period);
                    } catch (Exception e) {
                        log.warn("롱숏 비율 기동 시 조회 실패 - period: {}, error: {}", period, e.getMessage());
                    }
                }
                log.info("롱숏 비율 기동 시 최초 1회 조회 전체 완료");
            } catch (Exception e) {
                log.error("롱숏 비율 기동 시 조회 중 예외: {}", e.getMessage(), e);
            }
        }, "long-short-startup").start();
    }
}
