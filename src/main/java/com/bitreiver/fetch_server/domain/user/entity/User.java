package com.bitreiver.fetch_server.domain.user.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_nickname", columnList = "nickname")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;
    
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(name = "nickname", nullable = false, unique = true, length = 20)
    private String nickname;
    
    @Column(name = "signup_type", nullable = false)
    private Short signupType;
    
    @Column(name = "password_hash", columnDefinition = "text")
    private String passwordHash;
    
    @Column(name = "sns_provider")
    private Short snsProvider;
    
    @Column(name = "sns_id", length = 255)
    private String snsId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_trading_history_update_at")
    private LocalDateTime lastTradingHistoryUpdateAt;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "is_connect_exchange", nullable = false)
    @Builder.Default
    private Boolean isConnectExchange = false;
    
    @Type(JsonType.class)
    @Column(name = "connected_exchanges", columnDefinition = "jsonb")
    private String connectedExchanges;
    
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
    
    public void updateTradingHistorySyncTime() {
        this.lastTradingHistoryUpdateAt = LocalDateTime.now();
    }
}

