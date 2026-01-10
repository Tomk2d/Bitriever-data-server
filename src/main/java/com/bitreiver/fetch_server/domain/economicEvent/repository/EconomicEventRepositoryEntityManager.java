package com.bitreiver.fetch_server.domain.economicEvent.repository;

import java.time.LocalDate;
import java.util.List;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;

public interface EconomicEventRepositoryEntityManager {
    List<EconomicEvent> findUpcomingEvents(LocalDate today, int limit);
}
