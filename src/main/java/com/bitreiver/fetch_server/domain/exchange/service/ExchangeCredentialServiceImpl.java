package com.bitreiver.fetch_server.domain.exchange.service;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.entity.ExchangeCredential;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.repository.ExchangeCredentialRepository;
import com.bitreiver.fetch_server.domain.user.entity.User;
import com.bitreiver.fetch_server.domain.user.repository.UserRepository;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCredentialServiceImpl implements ExchangeCredentialService {
    
    private final ExchangeCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public ExchangeCredentialResponse saveCredentials(UUID userId, ExchangeCredentialRequest request) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
            
            String encryptedAccessKey = encryptionUtil.encrypt(request.getAccessKey());
            String encryptedSecretKey = encryptionUtil.encrypt(request.getSecretKey());
            
            Optional<ExchangeCredential> existing = credentialRepository
                .findByUserIdAndExchangeProvider(userId, request.getExchangeProvider());
            
            ExchangeCredential credentials;
            if (existing.isPresent()) {
                credentials = existing.get();
                credentials.setEncryptedAccessKey(encryptedAccessKey);
                credentials.setEncryptedSecretKey(encryptedSecretKey);
                credentials.updateTimestamp();
            } else {
                credentials = ExchangeCredential.builder()
                    .userId(userId)
                    .exchangeProvider(request.getExchangeProvider())
                    .encryptedAccessKey(encryptedAccessKey)
                    .encryptedSecretKey(encryptedSecretKey)
                    .createdAt(LocalDateTime.now())
                    .lastUpdatedAt(LocalDateTime.now())
                    .build();
            }
            
            ExchangeCredential saved = credentialRepository.save(credentials);
            
            ExchangeType exchangeType = ExchangeType.fromCode(request.getExchangeProvider());
            String providerName = exchangeType.name();
            
            List<String> currentExchanges = parseConnectedExchanges(user.getConnectedExchanges());
            if (!currentExchanges.contains(providerName)) {
                currentExchanges.add(providerName);
            }
            
            user.setIsConnectExchange(true);
            user.setConnectedExchanges(serializeConnectedExchanges(currentExchanges));
            userRepository.save(user);
            
            log.info("saveCredentials - 사용자 {}의 {} 연결 정보 업데이트 완료", userId, providerName);
            
            return toResponse(saved, true);
        } catch (CustomException e) {
            log.error("saveCredentials - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("saveCredentials - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자격증명 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<ExchangeCredentialResponse> getCredentials(UUID userId, Short exchangeProvider) {
        try {
            Optional<ExchangeCredential> credentials = credentialRepository
                .findByUserIdAndExchangeProvider(userId, exchangeProvider);
            
            if (credentials.isEmpty()) {
                return Optional.empty();
            }
            
            return Optional.of(toResponse(credentials.get(), true));
        } catch (Exception e) {
            log.error("getCredentials - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자격증명 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<ExchangeCredentialResponse> getAllCredentials(UUID userId) {
        try {
            List<ExchangeCredential> credentialsList = credentialRepository.findByUserId(userId);
            List<ExchangeCredentialResponse> responses = new ArrayList<>();
            
            for (ExchangeCredential credentials : credentialsList) {
                responses.add(toResponse(credentials, false));
            }
            
            return responses;
        } catch (Exception e) {
            log.error("getAllCredentials - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자격증명 목록 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public boolean deleteCredentials(UUID userId, Short exchangeProvider) {
        try {
            Optional<ExchangeCredential> credentials = credentialRepository
                .findByUserIdAndExchangeProvider(userId, exchangeProvider);
            
            if (credentials.isEmpty()) {
                return false;
            }
            
            credentialRepository.delete(credentials.get());
            
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                ExchangeType exchangeType = ExchangeType.fromCode(exchangeProvider);
                String providerName = exchangeType.name();
                
                List<String> currentExchanges = parseConnectedExchanges(user.getConnectedExchanges());
                currentExchanges.remove(providerName);
                
                if (currentExchanges.isEmpty()) {
                    user.setIsConnectExchange(false);
                }
                user.setConnectedExchanges(serializeConnectedExchanges(currentExchanges));
                userRepository.save(user);
            }
            
            return true;
        } catch (Exception e) {
            log.error("deleteCredentials - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자격증명 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public boolean verifyCredentials(UUID userId, Short exchangeProvider) {
        try {
            Optional<ExchangeCredential> credentials = credentialRepository
                .findByUserIdAndExchangeProvider(userId, exchangeProvider);
            
            if (credentials.isEmpty()) {
                return false;
            }
            
            try {
                encryptionUtil.decrypt(credentials.get().getEncryptedAccessKey());
                encryptionUtil.decrypt(credentials.get().getEncryptedSecretKey());
                return true;
            } catch (Exception e) {
                log.warn("verifyCredentials - 자격증명 복호화 실패: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("verifyCredentials - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "자격증명 검증 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private ExchangeCredentialResponse toResponse(ExchangeCredential credentials, boolean includeKeys) {
        ExchangeType exchangeType = ExchangeType.fromCode(credentials.getExchangeProvider());
        
        ExchangeCredentialResponse response = ExchangeCredentialResponse.from(credentials);
        
        ExchangeCredentialResponse.ExchangeCredentialResponseBuilder builder = response.toBuilder()
            .providerName(exchangeType.getName());
        
        if (includeKeys) {
            builder.accessKey(encryptionUtil.decrypt(credentials.getEncryptedAccessKey()))
                .secretKey(encryptionUtil.decrypt(credentials.getEncryptedSecretKey()));
        }
        
        return builder.build();
    }
    
    private List<String> parseConnectedExchanges(String connectedExchanges) {
        if (connectedExchanges == null || connectedExchanges.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(connectedExchanges, List.class);
        } catch (Exception e) {
            log.warn("parseConnectedExchanges - connectedExchanges 파싱 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private String serializeConnectedExchanges(List<String> exchanges) {
        try {
            return objectMapper.writeValueAsString(exchanges);
        } catch (Exception e) {
            log.warn("serializeConnectedExchanges - connectedExchanges 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }
}
