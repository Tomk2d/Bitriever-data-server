package com.bitreiver.fetch_server.infra.news;

import com.bitreiver.fetch_server.domain.article.enums.PublisherType;
import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BlockMediaCrawler implements NewsCrawler {
    
    private final WebClient blockMediaWebClient;
    private static final String BASE_URL = "https://www.blockmedia.co.kr";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern ARTICLE_ID_PATTERN = Pattern.compile("/archives/(\\d+)");
    
    public BlockMediaCrawler(@Qualifier("blockMediaWebClient") WebClient blockMediaWebClient) {
        this.blockMediaWebClient = blockMediaWebClient;
    }
    
    @Override
    public Mono<List<NewsCrawlerDto>> crawlArticles(int page) {
        String url = "/all-posts/page/" + page;
        
        log.info("블록미디어 크롤링 시작: page={}", page);
        
        return blockMediaWebClient
            .get()
            .uri(url)
            .exchangeToMono(response -> {
                HttpStatusCode status = response.statusCode();
                log.info("블록미디어 HTTP 응답: page={}, status={}", page, status);
                
                if (status.isError()) {
                    log.error("블록미디어 크롤링 HTTP 에러: page={}, status={}", page, status);
                    return response.createException()
                        .flatMap(Mono::error);
                }
                
                return response.bodyToMono(String.class)
                    .doOnNext(html -> log.debug("HTTP 응답 본문 수신: 길이={}", html != null ? html.length() : 0))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("블록미디어 크롤링: 응답 본문이 비어있음 - page={}", page);
                        return Mono.just("");
                    }));
            })
            .doOnError(error -> {
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException ex = (WebClientResponseException) error;
                    log.error("블록미디어 크롤링 실패: page={}, status={}, error={}", 
                        page, ex.getStatusCode(), ex.getMessage());
                } else {
                    log.error("블록미디어 크롤링 실패: page={}, error={}", page, error.getMessage(), error);
                }
            })
            .map(html -> {
                if (html == null || html.isEmpty()) {
                    log.warn("블록미디어 크롤링: 응답 본문이 비어있음 - page={}", page);
                    return new ArrayList<NewsCrawlerDto>();
                }
                
                Document doc = Jsoup.parse(html);
                Elements articles = doc.select("article.l-post");
                
                log.debug("파싱된 article 요소 수: {}", articles.size());
                
                List<NewsCrawlerDto> articleList = new ArrayList<>();
                
                for (Element article : articles) {
                    try {
                        NewsCrawlerDto dto = parseArticle(article);
                        if (dto != null && dto.getHeadline() != null && !dto.getHeadline().isEmpty()) {
                            articleList.add(dto);
                        }
                    } catch (Exception e) {
                        log.warn("기사 파싱 실패: {}", e.getMessage());
                    }
                }
                
                log.info("크롤링 완료: page={}, articles={}", page, articleList.size());
                return articleList;
            });
    }
    
    @Override
    public Mono<Integer> getLastPageNumber() {
        String url = "/all-posts/page/1";
        
        log.info("블록미디어 마지막 페이지 번호 조회 시작");
        
        return blockMediaWebClient
            .get()
            .uri(url)
            .exchangeToMono(response -> {
                HttpStatusCode status = response.statusCode();
                log.info("블록미디어 마지막 페이지 조회 HTTP 응답: status={}", status);
                
                if (status.isError()) {
                    log.error("블록미디어 마지막 페이지 조회 HTTP 에러: status={}", status);
                    return response.createException()
                        .flatMap(Mono::error);
                }
                
                return response.bodyToMono(String.class)
                    .doOnNext(html -> log.debug("HTTP 응답 본문 수신: 길이={}", html != null ? html.length() : 0))
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("블록미디어 마지막 페이지 조회: 응답 본문이 비어있음");
                        return Mono.just("");
                    }));
            })
            .doOnError(error -> {
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException ex = (WebClientResponseException) error;
                    log.error("마지막 페이지 번호 조회 실패: status={}, error={}", 
                        ex.getStatusCode(), ex.getMessage());
                } else {
                    log.error("마지막 페이지 번호 조회 실패: {}", error.getMessage(), error);
                }
            })
            .map(html -> {
                if (html == null || html.isEmpty()) {
                    log.warn("블록미디어 마지막 페이지 조회: 응답 본문이 비어있음");
                    return 1;
                }
                
                Document doc = Jsoup.parse(html);
                Elements pageLinks = doc.select("a.page-numbers[href*='/all-posts/page/']");
                
                int maxPage = 1;
                Pattern pagePattern = Pattern.compile("/all-posts/page/(\\d+)");
                
                for (Element link : pageLinks) {
                    String href = link.attr("href");
                    Matcher matcher = pagePattern.matcher(href);
                    if (matcher.find()) {
                        try {
                            int pageNum = Integer.parseInt(matcher.group(1));
                            maxPage = Math.max(maxPage, pageNum);
                        } catch (NumberFormatException e) {
                            // 무시
                        }
                    }
                }
                
                log.info("마지막 페이지 번호: {}", maxPage);
                return maxPage;
            });
    }
    
    private NewsCrawlerDto parseArticle(Element article) {
        NewsCrawlerDto.NewsCrawlerDtoBuilder builder = NewsCrawlerDto.builder();
        
        // 헤드라인과 링크 (h2.is-title.post-title 또는 h2.post-title)
        Element titleLink = article.selectFirst("h2.is-title.post-title a, h2.post-title a, h3.post-title a");
        if (titleLink == null) {
            return null;
        }
        
        String headline = titleLink.text().trim();
        String originalUrl = titleLink.attr("href");
        
        // 절대 URL로 변환
        if (!originalUrl.startsWith("http")) {
            originalUrl = BASE_URL + originalUrl;
        }
        
        builder.headline(headline);
        builder.originalUrl(originalUrl);
        
        // 기사 ID 추출
        Integer articleId = extractArticleId(originalUrl);
        builder.articleId(articleId);
        
        // 요약 (리스트 페이지에는 없음)
        builder.summary(null);
        
        // 기자명 추출 (span.meta-item.post-author > a)
        Element authorLink = article.selectFirst("span.meta-item.post-author a");
        if (authorLink != null) {
            String authorText = authorLink.text().trim();
            // "박수용 기자" -> "박수용"
            String reporterName = authorText.replaceAll("\\s*기자\\s*$", "").trim();
            if (!reporterName.isEmpty()) {
                builder.reporterName(reporterName);
            }
        }
        
        // 날짜 추출 (time.post-date의 datetime 속성)
        Element dateElement = article.selectFirst("time.post-date");
        if (dateElement != null) {
            String datetimeAttr = dateElement.attr("datetime");
            if (!datetimeAttr.isEmpty()) {
                // "2025-12-25 19:30" 형식
                LocalDateTime publishedAt = extractPublishedAtFromDatetime(datetimeAttr);
                builder.publishedAt(publishedAt);
            }
        }
        
        // 언론사 정보
        builder.publisherName("블록미디어");
        builder.publisherType(PublisherType.BLOCK_MEDIA.getCode());
        
        return builder.build();
    }
    
    private Integer extractArticleId(String url) {
        Matcher matcher = ARTICLE_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("기사 ID 파싱 실패: url={}", url);
            }
        }
        return null;
    }
    
    private LocalDateTime extractPublishedAtFromDatetime(String datetime) {
        if (datetime == null || datetime.isEmpty()) {
            return null;
        }
        
        try {
            // "2025-12-25 19:30" 형식
            return LocalDateTime.parse(datetime.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: datetime={}", datetime);
            return null;
        }
    }
    
}
