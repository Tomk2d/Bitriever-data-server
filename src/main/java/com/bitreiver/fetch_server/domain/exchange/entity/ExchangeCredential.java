package com.bitreiver.fetch_server.domain.exchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exchange_credentials")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeCredential {
    
    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;
    
    @Column(name = "exchange_provider", nullable = false)
    private Short exchangeProvider;
    
    @Setter
    @Column(name = "encrypted_access_key", nullable = false, columnDefinition = "text")
    private String encryptedAccessKey;
    
    @Setter
    @Column(name = "encrypted_secret_key", nullable = false, columnDefinition = "text")
    private String encryptedSecretKey;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;
    
    public void updateTimestamp() {
        this.lastUpdatedAt = LocalDateTime.now();
    }
}

