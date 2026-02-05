package com.bitreiver.fetch_server.domain.bithumb.service;

import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.infra.bithumb.BithumbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BithumbServiceImpl implements BithumbService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final int ORDER_PAGE_LIMIT = 100;
    private static final int RATE_LIMIT_EVERY_N = 25;

    private final BithumbClient bithumbClient;

    @Override
    public Mono<List<Map<String, Object>>> fetchAccounts(String accessKey, String secretKey) {
        try {
            Object response = bithumbClient.get("/v1/accounts", accessKey, secretKey, null, true)
                .block();

            if (response == null) {
                log.warn("fetchAccounts - Bithumb 계정 잔고 조회 결과가 null입니다");
                return Mono.just(new ArrayList<>());
            }

            List<Map<String, Object>> accounts;
            if (response instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) response;
                accounts = list;
            } else if (response instanceof Map) {
                accounts = Collections.singletonList((Map<String, Object>) response);
            } else {
                accounts = new ArrayList<>();
            }
            return Mono.just(accounts);
        } catch (CustomException e) {
            log.error("fetchAccounts - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAccounts - 예상치 못한 오류: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR,
                "빗썸 계정 잔고 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @Override
    public Mono<List<String>> fetchAllOrderUuids(String accessKey, String secretKey, LocalDateTime startTime) {
        try {
            if (startTime == null) {
                startTime = LocalDateTime.of(2017, 1, 1, 0, 0);
            }
            List<String> allUuids = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("states[]", Arrays.asList("done", "cancel"));
                params.put("page", page);
                params.put("limit", ORDER_PAGE_LIMIT);
                params.put("order_by", "desc");

                Object response = bithumbClient.get("/v1/orders", accessKey, secretKey, params, true)
                    .block();

                if (response == null || !(response instanceof List)) {
                    hasMore = false;
                    break;
                }

                List<?> list = (List<?>) response;
                if (list.isEmpty()) {
                    hasMore = false;
                    break;
                }

                boolean seenBeforeStartTime = false;
                for (Object r : list) {
                    if (!(r instanceof Map)) continue;
                    Map<String, Object> item = (Map<String, Object>) r;
                    Object executedVol = item.get("executed_volume");
                    if (executedVol == null || "0".equals(executedVol.toString())) continue;
                    Object uuid = item.get("uuid");
                    if (uuid == null) continue;
                    Object createdAtObj = item.get("created_at");
                    if (createdAtObj != null) {
                        LocalDateTime orderTime = parseCreatedAt(createdAtObj.toString());
                        if (orderTime != null && orderTime.isBefore(startTime)) {
                            seenBeforeStartTime = true;
                            break;
                        }
                    }
                    allUuids.add(uuid.toString());
                }
                if (seenBeforeStartTime) {
                    hasMore = false;
                }
                if (list.size() < ORDER_PAGE_LIMIT) {
                    hasMore = false;
                } else {
                    page++;
                }
                if (page % 5 == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return Mono.just(allUuids);
        } catch (CustomException e) {
            log.error("fetchAllOrderUuids - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAllOrderUuids - 예상치 못한 오류: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR,
                "빗썸 주문 UUID 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @Override
    public Mono<Map<String, Object>> fetchOrderByUuid(String accessKey, String secretKey, String uuid) {
        Map<String, Object> params = new HashMap<>();
        params.put("uuid", uuid);
        return bithumbClient.get("/v1/order", accessKey, secretKey, params, true)
            .map(response -> {
                if (response instanceof Map) {
                    return (Map<String, Object>) response;
                }
                return null;
            });
    }

    @Override
    public Mono<List<Map<String, Object>>> fetchAllTradingHistory(String accessKey, String secretKey, List<String> uuids) {
        try {
            if (uuids == null || uuids.isEmpty()) {
                return Mono.just(new ArrayList<>());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < uuids.size(); i++) {
                if (i > 0 && i % RATE_LIMIT_EVERY_N == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String uuid = uuids.get(i);
                Map<String, Object> order = fetchOrderByUuid(accessKey, secretKey, uuid).block();
                if (order != null) {
                    result.add(order);
                }
            }
            return Mono.just(result);
        } catch (CustomException e) {
            log.error("fetchAllTradingHistory - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAllTradingHistory - 예상치 못한 오류: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR,
                "빗썸 거래 내역 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    private static LocalDateTime parseCreatedAt(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value.replace("Z", ""), ISO_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
