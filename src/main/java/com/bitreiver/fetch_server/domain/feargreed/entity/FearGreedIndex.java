package com.bitreiver.fetch_server.domain.feargreed.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fear_greed_indices", 
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_fear_greed_date",
            columnNames = {"date"}
        )
    },
    indexes = {
        @Index(name = "idx_fear_greed_date", columnList = "date")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FearGreedIndex {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;
    
    @Column(name = "value", nullable = false)
    private Integer value;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

