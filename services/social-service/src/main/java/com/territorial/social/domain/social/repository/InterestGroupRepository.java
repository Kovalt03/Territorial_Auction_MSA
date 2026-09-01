package com.territorial.social.domain.social.repository;

import com.territorial.social.domain.social.entity.InterestGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestGroupRepository extends JpaRepository<InterestGroup, Long> {

    List<InterestGroup> findByUserId(Long userId);

    boolean existsByUserIdAndContinentId(Long userId, Long continentId);
}
