package com.bitreiver.fetch_server.domain.economicEvent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;

@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEvent, Long> {
    boolean existsByUniqueName(String uniqueName);
    
    Optional<EconomicEvent> findByUniqueName(String uniqueName);
}
