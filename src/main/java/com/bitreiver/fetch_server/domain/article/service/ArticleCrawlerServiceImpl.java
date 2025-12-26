package com.bitreiver.fetch_server.domain.article.service;

import com.bitreiver.fetch_server.infra.news.BlockMediaCrawler;
import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCrawlerServiceImpl implements ArticleCrawlerService {
    
    private final BlockMediaCrawler blockMediaCrawler;
    private final ArticleService articleService;
    
    @Override
    public Mono<Integer> initializeAllArticles() {
        return initializeAllArticles(1);
    }
    
    @Override
    public Mono<Integer> initializeAllArticles(int startPage) {
        log.info("초기화 크롤링 시작: 시작 페이지={}", startPage);
        
        return blockMediaCrawler.getLastPageNumber()
            .flatMap(lastPage -> {
                log.info("총 페이지 수: {}, 시작 페이지: {}", lastPage, startPage);
                if (startPage > lastPage) {
                    log.warn("시작 페이지({})가 마지막 페이지({})보다 큽니다.", startPage, lastPage);
                    return Mono.just(0);
                }
                return crawlAndSavePages(startPage, lastPage, false);
            })
            .doOnSuccess(totalSaved -> 
                log.info("초기화 크롤링 완료: 시작 페이지={}, 총 저장된 기사 수={}", startPage, totalSaved)
            )
            .doOnError(error -> 
                log.error("초기화 크롤링 실패: 시작 페이지={}", startPage, error)
            );
    }
    
    @Override
    public Mono<Integer> crawlNewArticles() {
        log.info("증분 크롤링 시작");
        
        AtomicInteger totalSaved = new AtomicInteger(0);
        
        return blockMediaCrawler.getLastPageNumber()
            .flatMap(lastPage -> {
                log.info("마지막 페이지 수: {}", lastPage);
                return crawlNewArticlesFromPage(1, lastPage, totalSaved);
            })
            .doOnSuccess(saved -> 
                log.info("증분 크롤링 완료: 총 저장된 기사 수={}", totalSaved.get())
            )
            .doOnError(error -> 
                log.error("증분 크롤링 실패", error)
            )
            .thenReturn(totalSaved.get());
    }
    
    /**
     * 특정 페이지 범위를 크롤링하고 저장합니다.
     * 병렬 처리로 여러 페이지를 동시에 처리합니다.
     * 500개 요청마다 10분 딜레이를 추가합니다.
     * 
     * @param startPage 시작 페이지
     * @param endPage 끝 페이지
     * @param skipExisting true면 이미 저장된 기사는 건너뜀
     * @return 저장된 총 기사 수
     */
    private Mono<Integer> crawlAndSavePages(int startPage, int endPage, boolean skipExisting) {
        AtomicInteger totalSaved = new AtomicInteger(0);
        
        // 동시성 10으로 설정하여 10개 페이지를 동시에 처리
        int concurrency = 10;
        int batchSize = 500; // 500개씩 묶어서 처리
        
        return Flux.range(startPage, endPage - startPage + 1)
            .buffer(batchSize) // 500개씩 묶음
            .index() // (batchIndex, pages) 튜플
            .flatMap(tuple -> {
                long batchIndex = tuple.getT1();
                List<Integer> pages = tuple.getT2();
                
                // 첫 번째 배치가 아니면 딜레이 추가
                Mono<List<Integer>> pagesMono = batchIndex > 0 
                    ? Mono.delay(Duration.ofMinutes(10))
                        .doOnSubscribe(s -> log.info("500개 요청 완료. 10분 대기 중... (배치 번호: {}, 시작 페이지: {})", 
                            batchIndex, pages.get(0)))
                        .doOnTerminate(() -> log.info("10분 대기 완료. 다음 500개 요청 시작... (배치 번호: {})", batchIndex))
                        .then(Mono.just(pages))
                    : Mono.just(pages);
                
                // 각 배치 내에서는 병렬 처리
                return pagesMono
                    .flatMapMany(Flux::fromIterable)
                    .flatMap(page -> crawlAndSavePage(page, skipExisting, totalSaved), concurrency);
            }, 1) // 배치는 순차적으로 처리 (딜레이를 위해)
            .then(Mono.fromCallable(totalSaved::get));
    }
    
    /**
     * 단일 페이지를 크롤링하고 저장합니다.
     */
    private Mono<Integer> crawlAndSavePage(int page, boolean skipExisting, AtomicInteger totalSaved) {
        return blockMediaCrawler.crawlArticles(page)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(retrySignal -> 
                    log.warn("페이지 {} 크롤링 재시도: {}", page, retrySignal.totalRetries() + 1)
                ))
            .map(articles -> {
                List<NewsCrawlerDto> articlesToSave = skipExisting 
                    ? articleService.filterNewArticles(articles)
                    : articles;
                
                int saved = articleService.saveArticles(articlesToSave);
                totalSaved.addAndGet(saved);
                log.info("페이지 {} 처리 완료: 크롤링={}, 저장={}", page, articles.size(), saved);
                return saved;
            })
            .onErrorResume(error -> {
                log.error("페이지 {} 크롤링 실패: {}", page, error.getMessage());
                return Mono.just(0);
            });
    }
    
    /**
     * 증분 크롤링: 1페이지부터 시작하여 새로운 기사만 크롤링합니다.
     * 이미 저장된 기사를 만나면 중단합니다.
     * 
     * @param startPage 시작 페이지
     * @param endPage 끝 페이지
     * @param totalSaved 저장된 기사 수 누적
     * @return Mono<Void>
     */
    private Mono<Void> crawlNewArticlesFromPage(int startPage, int endPage, AtomicInteger totalSaved) {
        return crawlNewArticlesRecursive(startPage, endPage, totalSaved);
    }
    
    /**
     * 재귀적으로 새로운 기사를 크롤링합니다.
     * 50개 요청마다 2초 딜레이를 추가합니다.
     */
    private Mono<Void> crawlNewArticlesRecursive(int currentPage, int endPage, AtomicInteger totalSaved, AtomicInteger requestCount) {
        if (currentPage > endPage) {
            return Mono.empty();
        }
        
        int currentRequestCount = requestCount.incrementAndGet();
        
        Mono<Void> crawlMono = blockMediaCrawler.crawlArticles(currentPage)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10)))
            .flatMap(articles -> {
                // 새로운 기사만 필터링
                List<NewsCrawlerDto> newArticles = articleService.filterNewArticles(articles);
                
                if (newArticles.isEmpty()) {
                    // 이 페이지에 새로운 기사가 없으면, 이미 저장된 기사가 있다는 의미
                    // 더 이상 새로운 기사가 없을 가능성이 높으므로 중단
                    log.info("페이지 {}에 새로운 기사가 없음. 증분 크롤링 중단", currentPage);
                    return Mono.empty();
                }
                
                // 새로운 기사 저장
                int saved = articleService.saveArticles(newArticles);
                totalSaved.addAndGet(saved);
                log.info("페이지 {} 처리 완료: 크롤링={}, 신규={}, 저장={}", 
                    currentPage, articles.size(), newArticles.size(), saved);
                
                // 다음 페이지로 재귀 호출
                return crawlNewArticlesRecursive(currentPage + 1, endPage, totalSaved, requestCount);
            })
            .onErrorResume(error -> {
                log.error("페이지 {} 크롤링 실패: {}", currentPage, error.getMessage());
                // 에러가 발생해도 다음 페이지 시도
                return crawlNewArticlesRecursive(currentPage + 1, endPage, totalSaved, requestCount);
            })
            .then();
        
        // 50개 요청마다 2초 딜레이
        if (currentRequestCount % 50 == 0) {
            log.info("50개 요청 완료. 2초 대기 중... (현재 페이지: {})", currentPage);
            return Mono.delay(Duration.ofSeconds(2))
                .then(crawlMono);
        }
        
        return crawlMono;
    }
    
    /**
     * 재귀적으로 새로운 기사를 크롤링합니다 (오버로드).
     */
    private Mono<Void> crawlNewArticlesRecursive(int currentPage, int endPage, AtomicInteger totalSaved) {
        return crawlNewArticlesRecursive(currentPage, endPage, totalSaved, new AtomicInteger(0));
    }
}

