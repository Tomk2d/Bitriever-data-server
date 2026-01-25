package com.bitreiver.fetch_server.domain.exchange.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExchangeCredentialId implements Serializable {
    private UUID userId;
    private Short exchangeProvider;
}
