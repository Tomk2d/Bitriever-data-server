package com.bitreiver.fetch_server.domain.economicEvent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "economic_events",
    indexes = {
        @Index(name = "idx_economic_events_date", columnList = "event_date"),
        @Index(name = "idx_economic_events_country_type", columnList = "country_type"),
        @Index(name = "idx_economic_events_date_country", columnList = "event_date, country_type")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "unique_name", nullable = false, unique = true, length = 255)
    private String uniqueName;
    
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    
    @Column(name = "title", nullable = false, length = 500)
    private String title;
    
    @Column(name = "subtitle_text", columnDefinition = "TEXT")
    private String subtitleText;
    
    @Column(name = "country_type", nullable = false, length = 10)
    private String countryType;
    
    @Column(name = "exclude_from_all")
    @Builder.Default
    private Boolean excludeFromAll = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToOne(mappedBy = "economicEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private EconomicEventValue economicEventValue;
    
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
