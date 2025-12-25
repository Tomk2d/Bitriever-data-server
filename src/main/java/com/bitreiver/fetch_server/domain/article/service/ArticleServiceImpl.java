package com.bitreiver.fetch_server.domain.article.service;

import com.bitreiver.fetch_server.domain.article.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.bitreiver.fetch_server.domain.article.entity.Article;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;

    @Transactional
    public int saveArticles(List<NewsCrawlerDto> articles) {
        if(articles == null || articles.isEmpty()) {
            return 0;
        }

        int savedCount = 0;
        int skippedCount = 0;

        for (NewsCrawlerDto dto : articles) {
            // publisherType + articleId 기준 중복 체크
            if (dto.getPublisherType() != null && dto.getArticleId() != null) {
                if (articleRepository.existsByPublisherTypeAndArticleId(
                        dto.getPublisherType(), dto.getArticleId())) {
                    skippedCount++;
                    continue;
                }
            } else {
                // articleId가 null인 경우 originalUrl로 대체 체크
                if (articleRepository.existsByOriginalUrl(dto.getOriginalUrl())) {
                    skippedCount++;
                    continue;
                }
            }
            
            Article article = Article.builder()
                .articleId(dto.getArticleId())
                .headline(dto.getHeadline())
                .summary(dto.getSummary())
                .originalUrl(dto.getOriginalUrl())
                .reporterName(dto.getReporterName())
                .publisherName(dto.getPublisherName())
                .publisherType(dto.getPublisherType())
                .publishedAt(dto.getPublishedAt())
                .build();
            
            articleRepository.save(article);
            savedCount++;
        }
        
        log.info("기사 저장 완료: 저장={}, 건너뜀={}, 총={}", savedCount, skippedCount, articles.size());
        return savedCount;
    }

    public List<NewsCrawlerDto> filterNewArticles(List<NewsCrawlerDto> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }
        
        return articles.stream()
            .filter(dto -> {
                // publisherType + articleId 기준 중복 체크
                if (dto.getPublisherType() != null && dto.getArticleId() != null) {
                    return !articleRepository.existsByPublisherTypeAndArticleId(
                            dto.getPublisherType(), dto.getArticleId());
                } else {
                    // articleId가 null인 경우 originalUrl로 대체 체크
                    return !articleRepository.existsByOriginalUrl(dto.getOriginalUrl());
                }
            })
            .collect(Collectors.toList());
    }
}
