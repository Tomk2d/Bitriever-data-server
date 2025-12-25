package com.bitreiver.fetch_server.domain.article.repository;

import com.bitreiver.fetch_server.domain.article.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    Optional<Article> findByOriginalUrl(String originalUrl);
    boolean existsByOriginalUrl(String originalUrl);
    
    boolean existsByPublisherTypeAndArticleId(Integer publisherType, Integer articleId);
    Optional<Article> findByPublisherTypeAndArticleId(Integer publisherType, Integer articleId);
}
