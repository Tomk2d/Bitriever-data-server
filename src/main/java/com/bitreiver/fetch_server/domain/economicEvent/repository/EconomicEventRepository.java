package com.bitreiver.fetch_server.domain.economicEvent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;

@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEvent, Long>, EconomicEventRepositoryEntityManager {
    boolean existsByUniqueName(String uniqueName);
    
    Optional<EconomicEvent> findByUniqueName(String uniqueName);

}
