package com.bitreiver.fetch_server.domain.article.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "articles",
    indexes = {
        @Index(name = "idx_articles_created_at", columnList = "created_at"),
        @Index(name = "idx_articles_original_url", columnList = "original_url"),
        @Index(name = "idx_articles_published_at", columnList = "published_at"),
        @Index(name = "idx_articles_publisher_type", columnList = "publisher_type"),
        @Index(name = "idx_articles_publisher_type_published_at", columnList = "publisher_type, published_at")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "article_id")
    private Integer articleId;
    
    @Column(name = "headline", nullable = false, length = 500)
    private String headline;
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    @Column(name = "original_url", nullable = false, length = 1000, unique = true)
    private String originalUrl;
    
    @Column(name = "reporter_name", length = 100)
    private String reporterName;
    
    @Column(name = "publisher_name", length = 100)
    private String publisherName;
    
    @Column(name = "publisher_type", nullable = false)
    private Integer publisherType;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
