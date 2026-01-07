package com.bitreiver.fetch_server.domain.economicIndex.service;

import com.bitreiver.fetch_server.domain.economicIndex.dto.EconomicIndexRedisDto;
import com.bitreiver.fetch_server.domain.economicIndex.enums.EconomicIndexType;

import java.time.LocalDate;
import java.util.List;

public interface EconomicIndexService {
    
    void fetchAndCacheByIndexType(EconomicIndexType indexType);
    
    void fetchAndCacheAll();
    
    List<EconomicIndexRedisDto> getByIndexType(EconomicIndexType indexType);
    
    List<EconomicIndexRedisDto> getByIndexTypeAndDateRange(
        EconomicIndexType indexType, 
        LocalDate startDate, 
        LocalDate endDate
    );
}