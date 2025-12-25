package com.bitreiver.fetch_server.domain.article.service;

import com.bitreiver.fetch_server.domain.article.enums.PublisherType;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.infra.news.BlockMediaCrawler;
import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ArticleCrawlerServiceTest {

    @MockBean
    private RedisCacheService redisCacheService;

    @MockBean
    private BlockMediaCrawler blockMediaCrawler;

    @MockBean
    private ArticleService articleService;

    @Autowired
    private ArticleCrawlerService articleCrawlerService;

    private List<NewsCrawlerDto> createMockArticles(int count) {
        List<NewsCrawlerDto> articles = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            NewsCrawlerDto article = NewsCrawlerDto.builder()
                .articleId(1000 + i)
                .headline("테스트 기사 " + i)
                .summary("테스트 요약 " + i)
                .originalUrl("https://www.blockmedia.co.kr/archives/" + (1000 + i))
                .reporterName("테스트 기자")
                .publisherName("블록미디어")
                .publisherType(PublisherType.BLOCK_MEDIA.getCode())
                .publishedAt(LocalDateTime.now().minusDays(i))
                .build();
            articles.add(article);
        }
        return articles;
    }

    @BeforeEach
    void setUp() {
        reset(blockMediaCrawler, articleService);
    }

    @Test
    @DisplayName("초기화 크롤링 테스트 - 3페이지 크롤링")
    void initializeAllArticles_success() {
        System.out.println("\n=== 초기화 크롤링 테스트 시작 ===\n");

        // Mock 설정
        int lastPage = 3;
        when(blockMediaCrawler.getLastPageNumber())
            .thenReturn(Mono.just(lastPage));

        // 각 페이지별로 기사 목록 반환
        when(blockMediaCrawler.crawlArticles(1))
            .thenReturn(Mono.just(createMockArticles(5)));
        when(blockMediaCrawler.crawlArticles(2))
            .thenReturn(Mono.just(createMockArticles(5)));
        when(blockMediaCrawler.crawlArticles(3))
            .thenReturn(Mono.just(createMockArticles(5)));

        // ArticleService Mock 설정
        when(articleService.saveArticles(any()))
            .thenAnswer(invocation -> {
                List<NewsCrawlerDto> articles = invocation.getArgument(0);
                return articles.size();
            });

        // 테스트 실행
        Integer totalSaved = articleCrawlerService.initializeAllArticles()
            .doOnSubscribe(subscription -> System.out.println("초기화 크롤링 Mono 구독 시작"))
            .doOnNext(result -> System.out.println("초기화 크롤링 완료: 저장된 기사 수 = " + result))
            .doOnError(error -> {
                System.err.println("\n=== 초기화 크롤링 에러 발생 ===");
                System.err.println("에러 메시지: " + error.getMessage());
                error.printStackTrace();
                System.err.println("================================\n");
            })
            .doOnTerminate(() -> System.out.println("초기화 크롤링 Mono 종료"))
            .block(Duration.ofSeconds(30));

        System.out.println("block() 결과: " + (totalSaved != null ? totalSaved : "null"));
        
        // 검증
        assertNotNull(totalSaved, "저장된 기사 수가 null입니다.");
        assertEquals(15, totalSaved, "저장된 기사 수가 예상과 다릅니다. (3페이지 * 5개 = 15개)");
        
        // Mock 호출 검증
        verify(blockMediaCrawler, times(1)).getLastPageNumber();
        verify(blockMediaCrawler, times(1)).crawlArticles(1);
        verify(blockMediaCrawler, times(1)).crawlArticles(2);
        verify(blockMediaCrawler, times(1)).crawlArticles(3);
        verify(articleService, times(3)).saveArticles(any());
        
        System.out.println("=== 초기화 크롤링 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("증분 크롤링 테스트 - 새로운 기사만 저장")
    void crawlNewArticles_success() {
        System.out.println("\n=== 증분 크롤링 테스트 시작 ===\n");

        // Mock 설정
        int lastPage = 3;
        when(blockMediaCrawler.getLastPageNumber())
            .thenReturn(Mono.just(lastPage));

        // 페이지 1: 새로운 기사 5개
        when(blockMediaCrawler.crawlArticles(1))
            .thenReturn(Mono.just(createMockArticles(5)));
        
        // 페이지 2: 새로운 기사 3개, 기존 기사 2개
        List<NewsCrawlerDto> page2Articles = new ArrayList<>();
        page2Articles.addAll(createMockArticles(3));
        // 기존 기사 2개는 filterNewArticles에서 제외될 것
        when(blockMediaCrawler.crawlArticles(2))
            .thenReturn(Mono.just(page2Articles));
        
        // 페이지 3: 새로운 기사 없음 (모두 기존 기사) - 여기서 중단
        when(blockMediaCrawler.crawlArticles(3))
            .thenReturn(Mono.just(new ArrayList<>()));

        // ArticleService Mock 설정
        // filterNewArticles: 페이지 1은 모두 신규, 페이지 2는 3개만 신규, 페이지 3은 없음
        when(articleService.filterNewArticles(any()))
            .thenAnswer(invocation -> {
                List<NewsCrawlerDto> articles = invocation.getArgument(0);
                // 페이지 1: 모두 신규
                if (articles.size() == 5 && articles.get(0).getArticleId() == 1001) {
                    return articles; // 모두 신규
                }
                // 페이지 2: 처음 3개만 신규
                if (articles.size() == 3) {
                    return articles.subList(0, 3);
                }
                // 페이지 3: 없음
                return new ArrayList<>();
            });

        when(articleService.saveArticles(any()))
            .thenAnswer(invocation -> {
                List<NewsCrawlerDto> articles = invocation.getArgument(0);
                return articles.size();
            });

        // 테스트 실행
        Integer totalSaved = articleCrawlerService.crawlNewArticles()
            .doOnSubscribe(subscription -> System.out.println("증분 크롤링 Mono 구독 시작"))
            .doOnNext(result -> System.out.println("증분 크롤링 완료: 저장된 기사 수 = " + result))
            .doOnError(error -> {
                System.err.println("\n=== 증분 크롤링 에러 발생 ===");
                System.err.println("에러 메시지: " + error.getMessage());
                error.printStackTrace();
                System.err.println("==============================\n");
            })
            .doOnTerminate(() -> System.out.println("증분 크롤링 Mono 종료"))
            .block(Duration.ofSeconds(30));

        System.out.println("block() 결과: " + (totalSaved != null ? totalSaved : "null"));
        
        // 검증
        assertNotNull(totalSaved, "저장된 기사 수가 null입니다.");
        assertTrue(totalSaved >= 5, "최소한 페이지 1의 5개 기사는 저장되어야 합니다.");
        
        // Mock 호출 검증
        verify(blockMediaCrawler, times(1)).getLastPageNumber();
        verify(blockMediaCrawler, atLeast(1)).crawlArticles(1);
        verify(articleService, atLeast(1)).filterNewArticles(any());
        verify(articleService, atLeast(1)).saveArticles(any());
        
        System.out.println("=== 증분 크롤링 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("초기화 크롤링 - 빈 페이지 처리 테스트")
    void initializeAllArticles_emptyPages() {
        System.out.println("\n=== 빈 페이지 처리 테스트 시작 ===\n");

        // Mock 설정
        int lastPage = 2;
        when(blockMediaCrawler.getLastPageNumber())
            .thenReturn(Mono.just(lastPage));

        // 페이지 1: 기사 있음
        when(blockMediaCrawler.crawlArticles(1))
            .thenReturn(Mono.just(createMockArticles(3)));
        
        // 페이지 2: 빈 페이지
        when(blockMediaCrawler.crawlArticles(2))
            .thenReturn(Mono.just(new ArrayList<>()));

        when(articleService.saveArticles(any()))
            .thenAnswer(invocation -> {
                List<NewsCrawlerDto> articles = invocation.getArgument(0);
                return articles.size();
            });

        // 테스트 실행
        Integer totalSaved = articleCrawlerService.initializeAllArticles()
            .block(Duration.ofSeconds(30));

        // 검증
        assertNotNull(totalSaved);
        assertEquals(3, totalSaved, "페이지 1의 3개 기사만 저장되어야 합니다.");
        
        verify(blockMediaCrawler, times(1)).getLastPageNumber();
        verify(blockMediaCrawler, times(1)).crawlArticles(1);
        verify(blockMediaCrawler, times(1)).crawlArticles(2);
        verify(articleService, times(2)).saveArticles(any());
        
        System.out.println("=== 빈 페이지 처리 테스트 완료 ===\n");
    }

    @Test
    @DisplayName("증분 크롤링 - 첫 페이지부터 새로운 기사 없음")
    void crawlNewArticles_noNewArticles() {
        System.out.println("\n=== 새로운 기사 없음 테스트 시작 ===\n");

        // Mock 설정
        int lastPage = 2;
        when(blockMediaCrawler.getLastPageNumber())
            .thenReturn(Mono.just(lastPage));

        // 모든 페이지에 새로운 기사 없음
        when(blockMediaCrawler.crawlArticles(1))
            .thenReturn(Mono.just(createMockArticles(3)));
        when(blockMediaCrawler.crawlArticles(2))
            .thenReturn(Mono.just(createMockArticles(3)));

        // filterNewArticles: 모든 페이지에서 빈 리스트 반환 (모두 기존 기사)
        when(articleService.filterNewArticles(any()))
            .thenReturn(new ArrayList<>());

        // 테스트 실행
        Integer totalSaved = articleCrawlerService.crawlNewArticles()
            .block(Duration.ofSeconds(30));

        // 검증
        assertNotNull(totalSaved);
        assertEquals(0, totalSaved, "새로운 기사가 없으므로 저장된 기사 수는 0이어야 합니다.");
        
        verify(blockMediaCrawler, times(1)).getLastPageNumber();
        verify(blockMediaCrawler, times(1)).crawlArticles(1);
        // 페이지 1에서 새로운 기사가 없으면 중단되므로 페이지 2는 호출되지 않음
        verify(blockMediaCrawler, never()).crawlArticles(2);
        
        System.out.println("=== 새로운 기사 없음 테스트 완료 ===\n");
    }
}

