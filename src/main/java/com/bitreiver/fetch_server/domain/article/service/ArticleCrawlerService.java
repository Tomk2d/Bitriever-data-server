package com.bitreiver.fetch_server.domain.article.service;

import reactor.core.publisher.Mono;

/**
 * 기사 크롤링 서비스 인터페이스
 * 초기화 크롤링과 증분 크롤링을 제공합니다.
 */
public interface ArticleCrawlerService {
    /**
     * 초기화 크롤링: 모든 페이지의 기사를 크롤링하고 저장합니다.
     * 
     * @return 저장된 총 기사 수
     */
    Mono<Integer> initializeAllArticles();
    
    /**
     * 초기화 크롤링: 지정된 시작 페이지부터 모든 페이지의 기사를 크롤링하고 저장합니다.
     * 
     * @param startPage 시작 페이지 번호
     * @return 저장된 총 기사 수
     */
    Mono<Integer> initializeAllArticles(int startPage);
    
    /**
     * 증분 크롤링: DB에 없는 새로운 기사만 크롤링하고 저장합니다.
     * 
     * @return 저장된 총 기사 수
     */
    Mono<Integer> crawlNewArticles();
}

