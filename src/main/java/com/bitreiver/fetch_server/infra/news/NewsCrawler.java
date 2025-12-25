package com.bitreiver.fetch_server.infra.news;

import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NewsCrawler {
    /**
     * 지정된 페이지의 기사 목록을 크롤링합니다.
     * 
     * @param page 페이지 번호 (1부터 시작)
     * @return 크롤링된 기사 목록
     */
    Mono<List<NewsCrawlerDto>> crawlArticles(int page);
    
    /**
     * 첫 페이지를 크롤링하여 마지막 페이지 번호를 확인합니다.
     * 
     * @return 마지막 페이지 번호
     */
    Mono<Integer> getLastPageNumber();
}