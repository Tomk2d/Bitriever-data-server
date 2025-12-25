package com.bitreiver.fetch_server.domain.article.controller;

import com.bitreiver.fetch_server.domain.article.service.ArticleCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 기사 크롤링 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/articles/crawl")
@RequiredArgsConstructor
public class ArticleCrawlerController {
    
    private final ArticleCrawlerService articleCrawlerService;
    
    /**
     * 초기화 크롤링 실행 (모든 페이지)
     */
    @PostMapping("/initialize")
    public Mono<ResponseEntity<Map<String, Object>>> initializeCrawl() {
        log.info("초기화 크롤링 요청 수신");
        
        return articleCrawlerService.initializeAllArticles()
            .map(totalSaved -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "초기화 크롤링 완료");
                response.put("totalSaved", totalSaved);
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                log.error("초기화 크롤링 실패", error);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "초기화 크롤링 실패: " + error.getMessage());
                return Mono.just(ResponseEntity.internalServerError().body(response));
            });
    }
    
    /**
     * 증분 크롤링 실행 (새로운 기사만)
     */
    @PostMapping("/incremental")
    public Mono<ResponseEntity<Map<String, Object>>> incrementalCrawl() {
        log.info("증분 크롤링 요청 수신");
        
        return articleCrawlerService.crawlNewArticles()
            .map(totalSaved -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "증분 크롤링 완료");
                response.put("totalSaved", totalSaved);
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                log.error("증분 크롤링 실패", error);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "증분 크롤링 실패: " + error.getMessage());
                return Mono.just(ResponseEntity.internalServerError().body(response));
            });
    }
}

