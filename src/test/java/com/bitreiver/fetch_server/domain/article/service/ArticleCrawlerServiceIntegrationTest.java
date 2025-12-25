package com.bitreiver.fetch_server.domain.article.service;

import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ArticleCrawlerServiceIntegrationTest {

    @MockBean
    private RedisCacheService redisCacheService;

    @Autowired
    private ArticleCrawlerService articleCrawlerService;

    @Test
    @DisplayName("초기화 크롤링 통합 테스트 - 실제 크롤링 (제한된 페이지)")
    void initializeAllArticles_integrationTest() {
        System.out.println("\n=== 초기화 크롤링 통합 테스트 시작 ===\n");
        System.out.println("주의: 실제 네트워크 요청이 발생하며 시간이 걸릴 수 있습니다.\n");

        Integer totalSaved = articleCrawlerService.initializeAllArticles()
            .doOnSubscribe(subscription -> System.out.println("초기화 크롤링 Mono 구독 시작"))
            .doOnNext(result -> {
                System.out.println("\n========================================");
                System.out.println("초기화 크롤링 완료");
                System.out.println("========================================");
                System.out.printf("총 저장된 기사 수: %d개\n", result);
                System.out.println("========================================\n");
            })
            .doOnError(error -> {
                System.err.println("\n=== 초기화 크롤링 에러 발생 ===");
                System.err.println("에러 메시지: " + error.getMessage());
                System.err.println("에러 타입: " + error.getClass().getName());
                error.printStackTrace();
                System.err.println("================================\n");
            })
            .doOnTerminate(() -> System.out.println("초기화 크롤링 Mono 종료"))
            .block(Duration.ofMinutes(30)); // 30분 타임아웃

        System.out.println("block() 결과: " + (totalSaved != null ? totalSaved + "개 저장됨" : "null"));

        assertNotNull(totalSaved, "저장된 기사 수가 null입니다.");
        assertTrue(totalSaved >= 0, "저장된 기사 수는 0 이상이어야 합니다.");

        System.out.println("\n=== 초기화 크롤링 통합 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("증분 크롤링 통합 테스트 - 실제 크롤링")
    void crawlNewArticles_integrationTest() {
        System.out.println("\n=== 증분 크롤링 통합 테스트 시작 ===\n");
        System.out.println("주의: 실제 네트워크 요청이 발생하며 시간이 걸릴 수 있습니다.\n");

        Integer totalSaved = articleCrawlerService.crawlNewArticles()
            .doOnSubscribe(subscription -> System.out.println("증분 크롤링 Mono 구독 시작"))
            .doOnNext(result -> {
                System.out.println("\n========================================");
                System.out.println("증분 크롤링 완료");
                System.out.println("========================================");
                System.out.printf("총 저장된 기사 수: %d개\n", result);
                System.out.println("========================================\n");
            })
            .doOnError(error -> {
                System.err.println("\n=== 증분 크롤링 에러 발생 ===");
                System.err.println("에러 메시지: " + error.getMessage());
                System.err.println("에러 타입: " + error.getClass().getName());
                error.printStackTrace();
                System.err.println("==============================\n");
            })
            .doOnTerminate(() -> System.out.println("증분 크롤링 Mono 종료"))
            .block(Duration.ofMinutes(30)); // 30분 타임아웃

        System.out.println("block() 결과: " + (totalSaved != null ? totalSaved + "개 저장됨" : "null"));

        assertNotNull(totalSaved, "저장된 기사 수가 null입니다.");
        assertTrue(totalSaved >= 0, "저장된 기사 수는 0 이상이어야 합니다.");

        System.out.println("\n=== 증분 크롤링 통합 테스트 완료 ===\n");
    }
}

