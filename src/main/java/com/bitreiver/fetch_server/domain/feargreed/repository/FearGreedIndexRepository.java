package com.bitreiver.fetch_server.domain.feargreed.repository;

import com.bitreiver.fetch_server.domain.feargreed.entity.FearGreedIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface FearGreedIndexRepository extends JpaRepository<FearGreedIndex, Integer> {
    Optional<FearGreedIndex> findByDate(LocalDate date);
    boolean existsByDate(LocalDate date);
}

