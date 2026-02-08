package com.bitreiver.fetch_server.domain.exchange.service;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.dto.RegisterAndSyncJobResult;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.sync.service.SyncService;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterAndSyncServiceImpl implements RegisterAndSyncService {

    private static final String REDIS_KEY_PREFIX = "exchange:register:job:";
    private static final int JOB_TTL_SECONDS = 300;

    private final ExchangeCredentialService exchangeCredentialService;
    private final com.bitreiver.fetch_server.domain.asset.service.AssetService assetService;
    private final SyncService syncService;
    private final RedisCacheService redisCacheService;
    private final RestTemplate restTemplate;

    @Value("${external.app.server.url:http://localhost:8080}")
    private String appServerUrl;

    @Override
    public String startRegisterAndSyncAsync(UUID userId, ExchangeCredentialRequest request) {
        String jobId = UUID.randomUUID().toString();
        ExchangeType exchangeType = ExchangeType.fromCode(request.getExchangeProvider().intValue());

        RegisterAndSyncJobResult initial = RegisterAndSyncJobResult.builder()
            .status(RegisterAndSyncJobResult.STATUS_PROCESSING)
            .userId(userId.toString())
            .exchangeProvider(request.getExchangeProvider())
            .exchangeName(exchangeType.getName())
            .build();
        redisCacheService.set(REDIS_KEY_PREFIX + jobId, initial, JOB_TTL_SECONDS);

        return jobId;
    }

    @Async("syncExecutor")
    public void runRegisterAndSync(String jobId, UUID userId, ExchangeCredentialRequest request) {
        Short exchangeProvider = request.getExchangeProvider();
        ExchangeType exchangeType = ExchangeType.fromCode(exchangeProvider.intValue());
        String exchangeName = exchangeType.getName();
        String exchangeNameEn = exchangeType.name();

        try {
            ExchangeCredentialResponse saved = exchangeCredentialService.saveCredentials(userId, request);

            try {
                assetService.syncAssetsForExchange(userId, exchangeProvider);
            } catch (Exception e) {
                String userMessage = toUserFriendlyMessage(e.getMessage());
                if (userMessage.contains("API 키 또는 시크릿 키")) {
                    log.warn("register-and-sync 자산 연동 실패 (API 키/시크릿 오류): jobId={}, userId={}, exchange={}", jobId, userId, exchangeName);
                } else {
                    log.error("register-and-sync 자산 연동 실패: jobId={}, userId={}, exchange={}, error={}", jobId, userId, exchangeName, e.getMessage(), e);
                }
                rollbackAndSaveFailed(jobId, userId, exchangeProvider, exchangeName, "ASSET_SYNC_FAILED", userMessage);
                return;
            }

            try {
                syncService.updateTradingHistoryForExchange(userId, exchangeNameEn);
            } catch (Exception e) {
                String userMessage = toUserFriendlyMessage(e.getMessage());
                if (userMessage.contains("API 키 또는 시크릿 키")) {
                    log.warn("register-and-sync 매매내역·수익률 연동 실패 (API 키/시크릿 오류): jobId={}, userId={}, exchange={}", jobId, userId, exchangeName);
                } else {
                    log.error("register-and-sync 매매내역·수익률 연동 실패: jobId={}, userId={}, exchange={}, error={}", jobId, userId, exchangeName, e.getMessage(), e);
                }
                rollbackAndSaveFailed(jobId, userId, exchangeProvider, exchangeName, "TRADING_HISTORY_SYNC_FAILED", userMessage);
                return;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("credential", saved);
            result.put("exchange_name", exchangeName);
            saveJobSuccess(jobId, userId, exchangeProvider, exchangeName, result);
            sendSyncCompleteCallback(userId, true, exchangeName, exchangeName + " 거래소 연동에 성공했습니다.");

        } catch (CustomException e) {
            log.error("register-and-sync 저장/연동 실패: jobId={}, userId={}, exchange={}, error={}",
                jobId, userId, exchangeName, e.getMessage());
            String userMessage = toUserFriendlyMessage(e.getMessage());
            rollbackAndSaveFailed(jobId, userId, exchangeProvider, exchangeName,
                e.getErrorCode() != null ? e.getErrorCode().getCode() : "REGISTER_SYNC_FAILED",
                userMessage);
        } catch (Exception e) {
            log.error("register-and-sync 예상치 못한 오류: jobId={}, userId={}, exchange={}, error={}",
                jobId, userId, exchangeName, e.getMessage(), e);
            String userMessage = toUserFriendlyMessage(e.getMessage());
            rollbackAndSaveFailed(jobId, userId, exchangeProvider, exchangeName, "INTERNAL_ERROR", userMessage);
        }
    }

    private void rollbackAndSaveFailed(String jobId, UUID userId, Short exchangeProvider, String exchangeName,
                                       String errorCode, String errorMessage) {
        try {
            exchangeCredentialService.rollbackCredentialSave(userId, exchangeProvider);
        } catch (Exception rollbackEx) {
            log.error("rollbackCredentialSave 실패: userId={}, exchange={}, error={}", userId, exchangeName, rollbackEx.getMessage());
        }
        RegisterAndSyncJobResult failed = RegisterAndSyncJobResult.builder()
            .status(RegisterAndSyncJobResult.STATUS_FAILED)
            .userId(userId.toString())
            .exchangeProvider(exchangeProvider)
            .exchangeName(exchangeName)
            .errorCode(errorCode)
            .error(errorMessage)
            .message(errorMessage != null ? errorMessage : "연동 실패로 등록이 취소되었습니다.")
            .build();
        redisCacheService.set(REDIS_KEY_PREFIX + jobId, failed, JOB_TTL_SECONDS);
    }

    private void saveJobSuccess(String jobId, UUID userId, Short exchangeProvider, String exchangeName,
                               Map<String, Object> result) {
        RegisterAndSyncJobResult success = RegisterAndSyncJobResult.builder()
            .status(RegisterAndSyncJobResult.STATUS_SUCCESS)
            .userId(userId.toString())
            .exchangeProvider(exchangeProvider)
            .exchangeName(exchangeName)
            .result(result)
            .message(exchangeName + " 거래소 연동에 성공했습니다.")
            .build();
        redisCacheService.set(REDIS_KEY_PREFIX + jobId, success, JOB_TTL_SECONDS);
    }

    private void sendSyncCompleteCallback(UUID userId, boolean success, String exchangeName, String message) {
        try {
            String url = appServerUrl + "/api/callback/sync-complete";
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId.toString());
            body.put("sync_type", "EXCHANGE_REGISTER_SYNC");
            body.put("success", success);
            body.put("message", message);
            body.put("exchange_name", exchangeName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
        } catch (Exception e) {
            log.warn("거래소 연동 완료 콜백 전송 실패: userId={}, error={}", userId, e.getMessage());
        }
    }

    private String toUserFriendlyMessage(String technicalMessage) {
        if (technicalMessage == null) return "연동에 실패했습니다.";
        if (technicalMessage.contains("JWT 토큰 생성 실패")
                || technicalMessage.contains("401 Unauthorized")
                || technicalMessage.contains("401 ")) {
            return "API Key 와 Secret Key 를 확인 후 재시도 해주세요.";
        }
        return technicalMessage;
    }

    @Override
    public RegisterAndSyncJobResult getRegisterStatus(String jobId) {
        return redisCacheService.get(REDIS_KEY_PREFIX + jobId, RegisterAndSyncJobResult.class).orElse(null);
    }
}
