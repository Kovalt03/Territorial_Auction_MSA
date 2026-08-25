package com.territorial.auction.domain.guild.repository;

import com.territorial.auction.domain.guild.entity.GuildMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {

    boolean existsByUser_IdAndStatusIn(Long userId, List<GuildMember.Status> statuses);

    boolean existsByUser_IdAndStatus(Long userId, GuildMember.Status status);

    boolean existsByUser_IdAndGuild_IdAndStatus(
            Long userId, Long guildId, GuildMember.Status status);

    Optional<GuildMember> findByUser_IdAndStatus(Long userId, GuildMember.Status status);

    Optional<GuildMember> findByUser_IdAndGuild_IdAndStatus(
            Long userId, Long guildId, GuildMember.Status status);

    long countByGuild_IdAndStatus(Long guildId, GuildMember.Status status);

    @Query(
            "SELECT gm.user.id FROM GuildMember gm WHERE gm.guild.id = :guildId AND gm.status = 'ACTIVE'")
    List<Long> findActiveUserIdsByGuildId(@Param("guildId") Long guildId);

    @Query(
            "SELECT gm FROM GuildMember gm JOIN FETCH gm.user WHERE gm.guild.id = :guildId AND gm.status = :status ORDER BY gm.joinedAt ASC")
    List<GuildMember> findByGuildIdAndStatusWithUser(
            @Param("guildId") Long guildId, @Param("status") GuildMember.Status status);
}
