package com.bitreiver.fetch_server.domain.economicEvent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "economic_event_values",
    indexes = {
        @Index(name = "idx_economic_event_values_ric", columnList = "ric"),
        @Index(name = "idx_economic_event_values_economic_event_id", columnList = "economic_event_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicEventValue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "economic_event_id", nullable = false, unique = true)
    private EconomicEvent economicEvent;
    
    @Column(name = "ric", nullable = false, length = 100)
    private String ric;
    
    @Column(name = "unit", length = 50)
    private String unit;
    
    @Column(name = "unit_prefix", length = 20)
    private String unitPrefix;
    
    @Column(name = "actual", precision = 20, scale = 4)
    private BigDecimal actual;
    
    @Column(name = "forecast", precision = 20, scale = 4)
    private BigDecimal forecast;
    
    @Column(name = "actual_forecast_diff", precision = 20, scale = 4)
    private BigDecimal actualForecastDiff;
    
    @Column(name = "historical", precision = 20, scale = 4)
    private BigDecimal historical;
    
    @Column(name = "time")
    private LocalTime time;
    
    @Column(name = "pre_announcement_wording", length = 50)
    private String preAnnouncementWording;
    
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
