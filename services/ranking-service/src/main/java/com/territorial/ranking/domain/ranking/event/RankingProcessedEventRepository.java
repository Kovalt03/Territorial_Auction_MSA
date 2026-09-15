package com.territorial.ranking.domain.ranking.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingProcessedEventRepository
        extends JpaRepository<RankingProcessedEvent, String> {}
