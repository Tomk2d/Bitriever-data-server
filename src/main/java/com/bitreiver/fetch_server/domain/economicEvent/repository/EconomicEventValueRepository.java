package com.bitreiver.fetch_server.domain.economicEvent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEventValue;

@Repository
public interface EconomicEventValueRepository extends JpaRepository<EconomicEventValue, Long> {
    
}
