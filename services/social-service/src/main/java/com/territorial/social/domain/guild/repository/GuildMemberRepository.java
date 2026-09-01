package com.territorial.social.domain.guild.repository;

import com.territorial.social.domain.guild.entity.GuildMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {

    boolean existsByUserIdAndStatusIn(Long userId, List<GuildMember.Status> statuses);

    boolean existsByUserIdAndStatus(Long userId, GuildMember.Status status);

    boolean existsByUserIdAndGuild_IdAndStatus(
            Long userId, Long guildId, GuildMember.Status status);

    Optional<GuildMember> findByUserIdAndStatus(Long userId, GuildMember.Status status);

    Optional<GuildMember> findByUserIdAndGuild_IdAndStatus(
            Long userId, Long guildId, GuildMember.Status status);

    long countByGuild_IdAndStatus(Long guildId, GuildMember.Status status);

    @Query(
            "SELECT gm.userId FROM GuildMember gm WHERE gm.guild.id = :guildId AND gm.status = 'ACTIVE'")
    List<Long> findActiveUserIdsByGuildId(@Param("guildId") Long guildId);

    List<GuildMember> findByGuild_IdAndStatusOrderByJoinedAtAsc(
            Long guildId, GuildMember.Status status);
}
