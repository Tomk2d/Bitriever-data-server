package com.bitreiver.fetch_server.domain.economicEvent.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDate;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;
import jakarta.persistence.TypedQuery;

@Repository
public class EconomicEventRepositoryEntityManagerImpl implements EconomicEventRepositoryEntityManager {
    
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<EconomicEvent> findUpcomingEvents(LocalDate today, int limit) {
        String jpql = "SELECT DISTINCT e FROM EconomicEvent e " +
                     "LEFT JOIN FETCH e.economicEventValue v " +
                     "WHERE e.eventDate >= :today " +
                     "ORDER BY e.eventDate ASC, e.countryType ASC";
        
        TypedQuery<EconomicEvent> query = entityManager.createQuery(jpql, EconomicEvent.class);
        query.setParameter("today", today);
        query.setMaxResults(limit);
        
        return query.getResultList();
    }
}
