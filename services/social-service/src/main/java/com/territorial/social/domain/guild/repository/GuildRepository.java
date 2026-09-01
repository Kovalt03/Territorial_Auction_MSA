package com.territorial.social.domain.guild.repository;

import com.territorial.social.domain.guild.entity.Guild;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    boolean existsByName(String name);

    Page<Guild> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
