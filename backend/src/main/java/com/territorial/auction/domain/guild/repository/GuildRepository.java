package com.territorial.auction.domain.guild.repository;

import com.territorial.auction.domain.guild.entity.Guild;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    boolean existsByName(String name);

    @Query("SELECT g FROM Guild g JOIN FETCH g.master")
    Page<Guild> findAllWithMaster(Pageable pageable);

    @Query(
            "SELECT g FROM Guild g JOIN FETCH g.master WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Guild> findByNameContainingWithMaster(@Param("name") String name, Pageable pageable);

    @Query("SELECT g FROM Guild g JOIN FETCH g.master WHERE g.id = :id")
    java.util.Optional<Guild> findByIdWithMaster(@Param("id") Long id);
}
