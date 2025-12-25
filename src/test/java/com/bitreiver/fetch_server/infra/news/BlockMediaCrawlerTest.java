package com.bitreiver.fetch_server.infra.news;

import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BlockMediaCrawlerTest {

    @MockBean
    private RedisCacheService redisCacheService;

    @Autowired
    private BlockMediaCrawler blockMediaCrawler;

    @Test
    @DisplayName("블록미디어 1페이지 크롤링 테스트")
    void crawlArticles_firstPage_success() {
        System.out.println("\n=== 블록미디어 크롤링 테스트 시작 ===\n");

        List<NewsCrawlerDto> articles = blockMediaCrawler.crawlArticles(1)
                .doOnSubscribe(subscription -> System.out.println("Mono 구독 시작"))
                .doOnNext(result -> System.out.println("Mono 성공: 기사 수 = " + (result != null ? result.size() : "null")))
                .doOnError(error -> {
                    System.err.println("\n=== 크롤링 에러 발생 ===");
                    System.err.println("에러 메시지: " + error.getMessage());
                    System.err.println("에러 타입: " + error.getClass().getName());
                    error.printStackTrace();
                    System.err.println("========================\n");
                })
                .doOnTerminate(() -> System.out.println("Mono 종료"))
                .onErrorReturn(new ArrayList<>())
                .block(Duration.ofSeconds(30));

        System.out.println("block() 결과: " + (articles != null ? "null이 아님, 크기=" + articles.size() : "null"));
        
        assertNotNull(articles, "기사 목록이 null입니다.");
        assertFalse(articles.isEmpty(), "기사 목록이 비어있습니다.");
        
        System.out.printf("크롤링된 기사 수: %d개\n\n", articles.size());
        
        for (int i = 0; i < articles.size(); i++) {
            NewsCrawlerDto article = articles.get(i);
            System.out.printf("--- 기사 %d ---\n", i + 1);
            System.out.printf("기사 ID: %s\n", article.getArticleId());
            System.out.printf("헤드라인: %s\n", article.getHeadline());
            System.out.printf("원본 URL: %s\n", article.getOriginalUrl());
            System.out.printf("요약: %s\n", article.getSummary() != null ? article.getSummary() : "(없음)");
            System.out.printf("기자명: %s\n", article.getReporterName() != null ? article.getReporterName() : "(없음)");
            System.out.printf("언론사명: %s\n", article.getPublisherName());
            System.out.printf("언론사 타입: %d\n", article.getPublisherType());
            System.out.printf("작성일자: %s\n", article.getPublishedAt() != null ? article.getPublishedAt().toString() : "(없음)");
            System.out.println();
            
            assertNotNull(article.getHeadline(), "헤드라인이 null입니다.");
            assertFalse(article.getHeadline().isEmpty(), "헤드라인이 비어있습니다.");
            assertNotNull(article.getOriginalUrl(), "원본 URL이 null입니다.");
            assertNotNull(article.getPublisherName(), "언론사명이 null입니다.");
            assertNotNull(article.getPublisherType(), "언론사 타입이 null입니다.");
        }
        
        System.out.println("=== 크롤링 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("블록미디어 마지막 페이지 번호 조회 테스트")
    void getLastPageNumber_success() {
        System.out.println("\n=== 마지막 페이지 번호 조회 테스트 시작 ===\n");

        Integer lastPage = blockMediaCrawler.getLastPageNumber()
                .doOnSubscribe(subscription -> System.out.println("Mono 구독 시작"))
                .doOnNext(result -> System.out.println("Mono 성공: 마지막 페이지 = " + result))
                .doOnError(error -> {
                    System.err.println("\n=== 마지막 페이지 조회 에러 발생 ===");
                    System.err.println("에러 메시지: " + error.getMessage());
                    System.err.println("에러 타입: " + error.getClass().getName());
                    error.printStackTrace();
                    System.err.println("==================================\n");
                })
                .doOnTerminate(() -> System.out.println("Mono 종료"))
                .onErrorReturn(1)
                .block(Duration.ofSeconds(30));

        System.out.println("block() 결과: " + (lastPage != null ? lastPage : "null"));
        
        assertNotNull(lastPage, "마지막 페이지 번호가 null입니다.");
        assertTrue(lastPage > 0, "마지막 페이지 번호가 0보다 작거나 같습니다.");
        
        System.out.printf("마지막 페이지 번호: %d\n", lastPage);
        System.out.println("\n=== 마지막 페이지 번호 조회 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("블록미디어 크롤링 상세 로그 테스트")
    void crawlArticles_detailedLog_success() {
        System.out.println("\n=== 상세 로그 테스트 시작 ===\n");

        blockMediaCrawler.crawlArticles(1)
                .doOnSubscribe(subscription -> System.out.println("Mono 구독 시작"))
                .doOnNext(articles -> {
                    System.out.println("========================================");
                    System.out.println("크롤링 결과 요약");
                    System.out.println("========================================");
                    System.out.printf("총 기사 수: %d개\n\n", articles != null ? articles.size() : 0);
                    
                    if (!articles.isEmpty()) {
                        NewsCrawlerDto firstArticle = articles.get(0);
                        System.out.println("첫 번째 기사 상세 정보:");
                        System.out.println("  - 기사 ID: " + firstArticle.getArticleId());
                        System.out.println("  - 헤드라인: " + firstArticle.getHeadline());
                        System.out.println("  - URL: " + firstArticle.getOriginalUrl());
                        System.out.println("  - 기자명: " + (firstArticle.getReporterName() != null ? firstArticle.getReporterName() : "없음"));
                        System.out.println("  - 작성일자: " + (firstArticle.getPublishedAt() != null ? firstArticle.getPublishedAt() : "없음"));
                        System.out.println("  - 언론사: " + firstArticle.getPublisherName() + " (타입: " + firstArticle.getPublisherType() + ")");
                        System.out.println();
                    }
                    
                    long withReporter = articles.stream()
                            .filter(a -> a.getReporterName() != null && !a.getReporterName().isEmpty())
                            .count();
                    long withDate = articles.stream()
                            .filter(a -> a.getPublishedAt() != null)
                            .count();
                    
                    System.out.println("통계:");
                    System.out.printf("  - 기자명 있는 기사: %d개 (%.1f%%)\n", 
                            withReporter, (withReporter * 100.0 / articles.size()));
                    System.out.printf("  - 작성일자 있는 기사: %d개 (%.1f%%)\n", 
                            withDate, (withDate * 100.0 / articles.size()));
                    System.out.println("========================================\n");
                })
                .doOnError(error -> {
                    System.err.println("크롤링 실패: " + error.getMessage());
                    error.printStackTrace();
                })
                .doOnTerminate(() -> System.out.println("Mono 종료"))
                .block(Duration.ofSeconds(30));
    }
}

