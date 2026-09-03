package com.territorial.social.domain.guild.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.social.client.MemberStatsClient;
import com.territorial.social.client.MemberStatsClient.MemberStat;
import com.territorial.social.domain.guild.dto.CreateGuildRequest;
import com.territorial.social.domain.guild.dto.CreateGuildResponse;
import com.territorial.social.domain.guild.dto.GuildApplicationListResponse;
import com.territorial.social.domain.guild.dto.GuildDetailResponse;
import com.territorial.social.domain.guild.dto.GuildListResponse;
import com.territorial.social.domain.guild.dto.JoinGuildRequest;
import com.territorial.social.domain.guild.dto.MyGuildResponse;
import com.territorial.social.domain.guild.dto.TransferMasterRequest;
import com.territorial.social.domain.guild.dto.UpdateGuildRequest;
import com.territorial.social.domain.guild.entity.Guild;
import com.territorial.social.domain.guild.entity.GuildMember;
import com.territorial.social.domain.guild.repository.GuildMemberRepository;
import com.territorial.social.domain.guild.repository.GuildRepository;
import com.territorial.social.domain.social.entity.ChatRoom;
import com.territorial.social.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.social.domain.social.repository.ChatRoomRepository;
import com.territorial.social.domain.user.entity.UserDisplay;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import com.territorial.social.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildService {

    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserDisplayRepository userDisplayRepository;
    private final MemberStatsClient memberStatsClient;

    @Transactional
    public CreateGuildResponse createGuild(Long userId, CreateGuildRequest request) {
        validateNotInGuild(userId);
        if (guildRepository.existsByName(request.name())) {
            throw new CustomException(ErrorCode.GUILD_NAME_DUPLICATED);
        }
        Guild guild =
                guildRepository.save(
                        Guild.builder()
                                .name(request.name())
                                .description(request.description())
                                .emblem(request.emblem())
                                .masterId(userId)
                                .build());
        guildMemberRepository.save(
                GuildMember.builder()
                        .guild(guild)
                        .userId(userId)
                        .role(GuildMember.Role.MASTER)
                        .status(GuildMember.Status.ACTIVE)
                        .build());
        chatRoomRepository.save(
                ChatRoom.builder().type(ChatRoomType.GUILD).targetId(guild.getId()).build());
        return new CreateGuildResponse(
                guild.getId(),
                guild.getName(),
                userId,
                resolveNickname(userId),
                1,
                guild.getCreatedAt());
    }

    public GuildListResponse getGuilds(String search, Pageable pageable) {
        Page<Guild> page =
                (search == null || search.isBlank())
                        ? guildRepository.findAll(pageable)
                        : guildRepository.findByNameContainingIgnoreCase(search, pageable);
        List<Guild> guilds = page.getContent();
        Map<Long, List<Long>> activeByGuild =
                guilds.stream()
                        .collect(
                                Collectors.toMap(
                                        Guild::getId,
                                        g ->
                                                guildMemberRepository.findActiveUserIdsByGuildId(
                                                        g.getId())));
        List<Long> allUserIds =
                Stream.concat(
                                activeByGuild.values().stream().flatMap(List::stream),
                                guilds.stream().map(Guild::getMasterId))
                        .distinct()
                        .toList();
        Map<Long, MemberStat> stats = memberStatsClient.fetch(allUserIds);
        Map<Long, String> nicknames = resolveNicknames(allUserIds.stream());
        List<GuildListResponse.GuildSummary> summaries =
                guilds.stream()
                        .map(g -> toSummary(g, activeByGuild.get(g.getId()), stats, nicknames))
                        .toList();
        return new GuildListResponse(
                page.getTotalElements(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                summaries);
    }

    public GuildDetailResponse getGuildDetail(Long guildId) {
        Guild guild = findGuildOrThrow(guildId);
        List<GuildMember> members =
                guildMemberRepository.findByGuild_IdAndStatusOrderByJoinedAtAsc(
                        guildId, GuildMember.Status.ACTIVE);
        List<Long> memberUserIds = members.stream().map(GuildMember::getUserId).toList();
        Map<Long, MemberStat> stats = memberStatsClient.fetch(memberUserIds);
        Map<Long, String> nicknames = resolveNicknames(memberUserIds.stream());
        long totalTerritoryCount =
                memberUserIds.stream().mapToLong(id -> territoryOf(stats, id)).sum();
        List<GuildDetailResponse.MemberInfo> memberInfos =
                members.stream()
                        .map(
                                m ->
                                        new GuildDetailResponse.MemberInfo(
                                                m.getUserId(),
                                                nicknames.getOrDefault(
                                                        m.getUserId(), UNKNOWN_NICKNAME),
                                                m.getRole().name(),
                                                territoryOf(stats, m.getUserId()),
                                                m.getJoinedAt()))
                        .toList();
        return new GuildDetailResponse(
                guild.getId(),
                guild.getName(),
                guild.getDescription(),
                guild.getEmblem(),
                new GuildDetailResponse.MasterInfo(
                        guild.getMasterId(), resolveNickname(guild.getMasterId())),
                members.size(),
                totalTerritoryCount,
                memberInfos,
                guild.getCreatedAt());
    }

    public MyGuildResponse getMyGuild(Long userId) {
        GuildMember member =
                guildMemberRepository
                        .findByUserIdAndStatus(userId, GuildMember.Status.ACTIVE)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOT_IN_GUILD));
        Guild guild = findGuildOrThrow(member.getGuild().getId());
        List<Long> activeUserIds = guildMemberRepository.findActiveUserIdsByGuildId(guild.getId());
        Map<Long, MemberStat> stats = memberStatsClient.fetch(activeUserIds);
        return new MyGuildResponse(
                guild.getId(),
                guild.getName(),
                guild.getDescription(),
                resolveNickname(guild.getMasterId()),
                activeUserIds.size(),
                guild.getMaxMembers(),
                sumTerritories(stats),
                sumTrophy(stats),
                member.getRole().name(),
                member.getJoinedAt());
    }

    @Transactional
    public void joinGuild(Long userId, Long guildId, JoinGuildRequest request) {
        Guild guild = findGuildOrThrow(guildId);
        if (guildMemberRepository.existsByUserIdAndStatus(userId, GuildMember.Status.ACTIVE)) {
            throw new CustomException(ErrorCode.ALREADY_IN_GUILD);
        }
        if (guildMemberRepository.existsByUserIdAndGuild_IdAndStatus(
                userId, guildId, GuildMember.Status.PENDING)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }
        guildMemberRepository.save(
                GuildMember.builder()
                        .guild(guild)
                        .userId(userId)
                        .role(GuildMember.Role.MEMBER)
                        .status(GuildMember.Status.PENDING)
                        .message(request != null ? request.message() : null)
                        .build());
    }

    @Transactional
    public void approveApplication(Long masterId, Long guildId, Long targetUserId) {
        Guild guild = findGuildOrThrow(guildId);
        validateMaster(guild, masterId);
        GuildMember application = findPendingApplicationOrThrow(targetUserId, guildId);
        long activeCount =
                guildMemberRepository.countByGuild_IdAndStatus(guildId, GuildMember.Status.ACTIVE);
        if (activeCount >= guild.getMaxMembers()) {
            throw new CustomException(ErrorCode.GUILD_FULL);
        }
        application.approve();
    }

    public GuildApplicationListResponse getApplications(Long masterId, Long guildId) {
        Guild guild = findGuildOrThrow(guildId);
        validateMaster(guild, masterId);
        List<GuildMember> applications =
                guildMemberRepository.findByGuild_IdAndStatusOrderByJoinedAtAsc(
                        guildId, GuildMember.Status.PENDING);
        List<Long> applicantUserIds = applications.stream().map(GuildMember::getUserId).toList();
        Map<Long, MemberStat> stats = memberStatsClient.fetch(applicantUserIds);
        Map<Long, String> nicknames = resolveNicknames(applicantUserIds.stream());
        List<GuildApplicationListResponse.ApplicationInfo> infos =
                applications.stream()
                        .map(
                                m ->
                                        new GuildApplicationListResponse.ApplicationInfo(
                                                m.getId(),
                                                m.getUserId(),
                                                nicknames.getOrDefault(
                                                        m.getUserId(), UNKNOWN_NICKNAME),
                                                (int) trophyOf(stats, m.getUserId()),
                                                m.getJoinedAt()))
                        .toList();
        return new GuildApplicationListResponse(guildId, infos);
    }

    @Transactional
    public void rejectApplication(Long masterId, Long guildId, Long targetUserId) {
        validateMaster(findGuildOrThrow(guildId), masterId);
        findPendingApplicationOrThrow(targetUserId, guildId).cancel();
    }

    @Transactional
    public void transferMaster(Long currentMasterId, Long guildId, TransferMasterRequest request) {
        Guild guild = findGuildOrThrow(guildId);
        validateMaster(guild, currentMasterId);
        if (currentMasterId.equals(request.newMasterId())) {
            throw new CustomException(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }
        GuildMember currentMasterMember = findActiveMemberOrThrow(currentMasterId, guildId);
        GuildMember newMasterMember = findActiveMemberOrThrow(request.newMasterId(), guildId);
        guild.transferMaster(newMasterMember.getUserId());
        currentMasterMember.demoteToMember();
        newMasterMember.promoteToMaster();
    }

    @Transactional
    public void kickMember(Long masterId, Long guildId, Long targetUserId) {
        Guild guild = findGuildOrThrow(guildId);
        validateMaster(guild, masterId);
        GuildMember target = findActiveMemberOrThrow(targetUserId, guildId);
        if (target.getRole() == GuildMember.Role.MASTER) {
            throw new CustomException(ErrorCode.CANNOT_KICK_MASTER);
        }
        target.kick();
    }

    @Transactional
    public void updateGuild(Long masterId, Long guildId, UpdateGuildRequest request) {
        Guild guild = findGuildOrThrow(guildId);
        validateMaster(guild, masterId);
        Guild.RecruitingStatus recruitingStatus =
                request.recruitingStatus() != null
                        ? Guild.RecruitingStatus.valueOf(request.recruitingStatus())
                        : null;
        guild.updateInfo(request.description(), request.emblem(), recruitingStatus);
    }

    @Transactional
    public void leaveGuild(Long userId, Long guildId) {
        findGuildOrThrow(guildId);
        GuildMember member = findActiveMemberOrThrow(userId, guildId);
        if (member.getRole() == GuildMember.Role.MASTER) {
            validateMasterCanLeave(guildId);
        }
        member.leave();
    }

    @Transactional
    public void cancelJoinApplication(Long userId, Long guildId) {
        findPendingApplicationOrThrow(userId, guildId).cancel();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private GuildListResponse.GuildSummary toSummary(
            Guild guild,
            List<Long> activeUserIds,
            Map<Long, MemberStat> stats,
            Map<Long, String> nicknames) {
        long trophy = activeUserIds.stream().mapToLong(id -> trophyOf(stats, id)).sum();
        long territories = activeUserIds.stream().mapToLong(id -> territoryOf(stats, id)).sum();
        return new GuildListResponse.GuildSummary(
                guild.getId(),
                guild.getName(),
                nicknames.getOrDefault(guild.getMasterId(), UNKNOWN_NICKNAME),
                activeUserIds.size(),
                guild.getMaxMembers(),
                trophy,
                territories,
                guild.getRecruitingStatus().name());
    }

    private long territoryOf(Map<Long, MemberStat> stats, Long userId) {
        MemberStat s = stats.get(userId);
        return s == null ? 0 : s.territoryCount();
    }

    private long trophyOf(Map<Long, MemberStat> stats, Long userId) {
        MemberStat s = stats.get(userId);
        return s == null ? 0 : s.trophyScore();
    }

    private long sumTerritories(Map<Long, MemberStat> stats) {
        return stats.values().stream().mapToLong(MemberStat::territoryCount).sum();
    }

    private long sumTrophy(Map<Long, MemberStat> stats) {
        return stats.values().stream().mapToLong(MemberStat::trophyScore).sum();
    }

    private String resolveNickname(Long userId) {
        return userDisplayRepository
                .findById(userId)
                .map(UserDisplay::getNickname)
                .orElse(UNKNOWN_NICKNAME);
    }

    private Map<Long, String> resolveNicknames(Stream<Long> userIds) {
        List<Long> ids = userIds.distinct().toList();
        return userDisplayRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserDisplay::getUserId, UserDisplay::getNickname));
    }

    private Guild findGuildOrThrow(Long guildId) {
        return guildRepository
                .findById(guildId)
                .orElseThrow(() -> new CustomException(ErrorCode.GUILD_NOT_FOUND));
    }

    private void validateNotInGuild(Long userId) {
        if (guildMemberRepository.existsByUserIdAndStatusIn(
                userId, List.of(GuildMember.Status.ACTIVE, GuildMember.Status.PENDING))) {
            throw new CustomException(ErrorCode.ALREADY_IN_GUILD);
        }
    }

    private GuildMember findPendingApplicationOrThrow(Long userId, Long guildId) {
        return guildMemberRepository
                .findByUserIdAndGuild_IdAndStatus(userId, guildId, GuildMember.Status.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private GuildMember findActiveMemberOrThrow(Long userId, Long guildId) {
        return guildMemberRepository
                .findByUserIdAndGuild_IdAndStatus(userId, guildId, GuildMember.Status.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_IN_GUILD));
    }

    private void validateMasterCanLeave(Long guildId) {
        long activeCount =
                guildMemberRepository.countByGuild_IdAndStatus(guildId, GuildMember.Status.ACTIVE);
        if (activeCount > 1) {
            throw new CustomException(ErrorCode.GUILD_MASTER_CANNOT_LEAVE);
        }
    }

    private void validateMaster(Guild guild, Long requesterId) {
        if (!guild.getMasterId().equals(requesterId)) {
            throw new CustomException(ErrorCode.NOT_GUILD_MASTER);
        }
    }
}
