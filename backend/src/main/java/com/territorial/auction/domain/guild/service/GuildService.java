package com.territorial.auction.domain.guild.service;

import com.territorial.auction.domain.guild.dto.CreateGuildRequest;
import com.territorial.auction.domain.guild.dto.CreateGuildResponse;
import com.territorial.auction.domain.guild.dto.GuildApplicationListResponse;
import com.territorial.auction.domain.guild.dto.GuildDetailResponse;
import com.territorial.auction.domain.guild.dto.GuildListResponse;
import com.territorial.auction.domain.guild.dto.JoinGuildRequest;
import com.territorial.auction.domain.guild.dto.MyGuildResponse;
import com.territorial.auction.domain.guild.dto.TransferMasterRequest;
import com.territorial.auction.domain.guild.dto.UpdateGuildRequest;
import com.territorial.auction.domain.guild.entity.Guild;
import com.territorial.auction.domain.guild.entity.GuildMember;
import com.territorial.auction.domain.guild.repository.GuildMemberRepository;
import com.territorial.auction.domain.guild.repository.GuildRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.social.entity.ChatRoom;
import com.territorial.auction.domain.social.entity.ChatRoom.ChatRoomType;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;
    private final UserTrophyRepository userTrophyRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public CreateGuildResponse createGuild(Long userId, CreateGuildRequest request) {
        validateNotInGuild(userId);
        if (guildRepository.existsByName(request.name())) {
            throw new CustomException(ErrorCode.GUILD_NAME_DUPLICATED);
        }
        User user = findUserOrThrow(userId);
        Guild guild =
                guildRepository.save(
                        Guild.builder()
                                .name(request.name())
                                .description(request.description())
                                .emblem(request.emblem())
                                .master(user)
                                .build());
        guildMemberRepository.save(
                GuildMember.builder()
                        .guild(guild)
                        .user(user)
                        .role(GuildMember.Role.MASTER)
                        .status(GuildMember.Status.ACTIVE)
                        .build());
        chatRoomRepository.save(
                ChatRoom.builder().type(ChatRoomType.GUILD).targetId(guild.getId()).build());
        return new CreateGuildResponse(
                guild.getId(),
                guild.getName(),
                user.getId(),
                user.getNickname(),
                1,
                guild.getCreatedAt());
    }

    public GuildListResponse getGuilds(String search, Pageable pageable) {
        Page<Guild> page =
                (search == null || search.isBlank())
                        ? guildRepository.findAllWithMaster(pageable)
                        : guildRepository.findByNameContainingWithMaster(search, pageable);
        List<GuildListResponse.GuildSummary> summaries =
                page.getContent().stream().map(this::toGuildSummary).toList();
        return new GuildListResponse(
                page.getTotalElements(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                summaries);
    }

    public GuildDetailResponse getGuildDetail(Long guildId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        List<GuildMember> members =
                guildMemberRepository.findByGuildIdAndStatusWithUser(
                        guildId, GuildMember.Status.ACTIVE);
        List<Long> memberUserIds = members.stream().map(m -> m.getUser().getId()).toList();
        Map<Long, Long> territoryCountMap =
                memberUserIds.isEmpty()
                        ? Map.of()
                        : territoryRepository
                                .countGroupByOwnerIds(
                                        memberUserIds, Territory.TerritoryStatus.OCCUPIED)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                r -> (Long) r[0],
                                                r -> ((Number) r[1]).longValue()));
        long totalTerritoryCount =
                territoryCountMap.values().stream().mapToLong(Long::longValue).sum();
        List<GuildDetailResponse.MemberInfo> memberInfos =
                members.stream()
                        .map(
                                m ->
                                        new GuildDetailResponse.MemberInfo(
                                                m.getUser().getId(),
                                                m.getUser().getNickname(),
                                                m.getRole().name(),
                                                territoryCountMap.getOrDefault(
                                                        m.getUser().getId(), 0L),
                                                m.getJoinedAt()))
                        .toList();
        return new GuildDetailResponse(
                guild.getId(),
                guild.getName(),
                guild.getDescription(),
                guild.getEmblem(),
                new GuildDetailResponse.MasterInfo(
                        guild.getMaster().getId(), guild.getMaster().getNickname()),
                members.size(),
                totalTerritoryCount,
                memberInfos,
                guild.getCreatedAt());
    }

    public MyGuildResponse getMyGuild(Long userId) {
        GuildMember member =
                guildMemberRepository
                        .findByUser_IdAndStatus(userId, GuildMember.Status.ACTIVE)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOT_IN_GUILD));
        Guild guild = findGuildWithMasterOrThrow(member.getGuild().getId());
        List<Long> activeUserIds = guildMemberRepository.findActiveUserIdsByGuildId(guild.getId());
        return new MyGuildResponse(
                guild.getId(),
                guild.getName(),
                guild.getDescription(),
                guild.getMaster().getNickname(),
                activeUserIds.size(),
                guild.getMaxMembers(),
                countTerritories(activeUserIds),
                sumTrophyPoints(activeUserIds),
                member.getRole().name(),
                member.getJoinedAt());
    }

    @Transactional
    public void joinGuild(Long userId, Long guildId, JoinGuildRequest request) {
        Guild guild = findGuildOrThrow(guildId);
        if (guildMemberRepository.existsByUser_IdAndStatus(userId, GuildMember.Status.ACTIVE)) {
            throw new CustomException(ErrorCode.ALREADY_IN_GUILD);
        }
        if (guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                userId, guildId, GuildMember.Status.PENDING)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }
        User user = findUserOrThrow(userId);
        guildMemberRepository.save(
                GuildMember.builder()
                        .guild(guild)
                        .user(user)
                        .role(GuildMember.Role.MEMBER)
                        .status(GuildMember.Status.PENDING)
                        .message(request != null ? request.message() : null)
                        .build());
    }

    @Transactional
    public void approveApplication(Long masterId, Long guildId, Long targetUserId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, masterId);
        GuildMember application =
                guildMemberRepository
                        .findByUser_IdAndGuild_IdAndStatus(
                                targetUserId, guildId, GuildMember.Status.PENDING)
                        .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        long activeCount =
                guildMemberRepository.countByGuild_IdAndStatus(guildId, GuildMember.Status.ACTIVE);
        if (activeCount >= guild.getMaxMembers()) {
            throw new CustomException(ErrorCode.GUILD_FULL);
        }
        application.approve();
    }

    public GuildApplicationListResponse getApplications(Long masterId, Long guildId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, masterId);
        List<GuildMember> applications =
                guildMemberRepository.findByGuildIdAndStatusWithUser(
                        guildId, GuildMember.Status.PENDING);
        List<Long> applicantUserIds = applications.stream().map(m -> m.getUser().getId()).toList();
        Map<Long, Long> trophyScoreMap =
                applicantUserIds.isEmpty()
                        ? Map.of()
                        : userTrophyRepository.sumScoreGroupByUserIds(applicantUserIds).stream()
                                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        List<GuildApplicationListResponse.ApplicationInfo> infos =
                applications.stream()
                        .map(
                                m ->
                                        new GuildApplicationListResponse.ApplicationInfo(
                                                m.getId(),
                                                m.getUser().getId(),
                                                m.getUser().getNickname(),
                                                trophyScoreMap
                                                        .getOrDefault(m.getUser().getId(), 0L)
                                                        .intValue(),
                                                m.getJoinedAt()))
                        .toList();
        return new GuildApplicationListResponse(guildId, infos);
    }

    @Transactional
    public void rejectApplication(Long masterId, Long guildId, Long targetUserId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, masterId);
        GuildMember application = findPendingApplicationOrThrow(targetUserId, guildId);
        application.cancel();
    }

    @Transactional
    public void transferMaster(Long currentMasterId, Long guildId, TransferMasterRequest request) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, currentMasterId);
        if (currentMasterId.equals(request.newMasterId())) {
            throw new CustomException(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }
        GuildMember currentMasterMember = findActiveMemberOrThrow(currentMasterId, guildId);
        GuildMember newMasterMember = findActiveMemberOrThrow(request.newMasterId(), guildId);
        guild.transferMaster(newMasterMember.getUser());
        currentMasterMember.demoteToMember();
        newMasterMember.promoteToMaster();
    }

    @Transactional
    public void kickMember(Long masterId, Long guildId, Long targetUserId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, masterId);
        GuildMember target = findActiveMemberOrThrow(targetUserId, guildId);
        if (target.getRole() == GuildMember.Role.MASTER) {
            throw new CustomException(ErrorCode.CANNOT_KICK_MASTER);
        }
        target.kick();
    }

    @Transactional
    public void updateGuild(Long masterId, Long guildId, UpdateGuildRequest request) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        validateMaster(guild, masterId);
        Guild.RecruitingStatus recruitingStatus =
                request.recruitingStatus() != null
                        ? Guild.RecruitingStatus.valueOf(request.recruitingStatus())
                        : null;
        guild.updateInfo(request.description(), request.emblem(), recruitingStatus);
    }

    @Transactional
    public void leaveGuild(Long userId, Long guildId) {
        Guild guild = findGuildWithMasterOrThrow(guildId);
        GuildMember member = findActiveMemberOrThrow(userId, guildId);
        if (member.getRole() == GuildMember.Role.MASTER) {
            validateMasterCanLeave(guildId);
        }
        member.leave();
    }

    @Transactional
    public void cancelJoinApplication(Long userId, Long guildId) {
        GuildMember application =
                guildMemberRepository
                        .findByUser_IdAndGuild_IdAndStatus(
                                userId, guildId, GuildMember.Status.PENDING)
                        .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        application.cancel();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Guild findGuildOrThrow(Long guildId) {
        return guildRepository
                .findById(guildId)
                .orElseThrow(() -> new CustomException(ErrorCode.GUILD_NOT_FOUND));
    }

    private Guild findGuildWithMasterOrThrow(Long guildId) {
        return guildRepository
                .findByIdWithMaster(guildId)
                .orElseThrow(() -> new CustomException(ErrorCode.GUILD_NOT_FOUND));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateNotInGuild(Long userId) {
        if (guildMemberRepository.existsByUser_IdAndStatusIn(
                userId, List.of(GuildMember.Status.ACTIVE, GuildMember.Status.PENDING))) {
            throw new CustomException(ErrorCode.ALREADY_IN_GUILD);
        }
    }

    private GuildMember findPendingApplicationOrThrow(Long userId, Long guildId) {
        return guildMemberRepository
                .findByUser_IdAndGuild_IdAndStatus(userId, guildId, GuildMember.Status.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private GuildMember findActiveMemberOrThrow(Long userId, Long guildId) {
        return guildMemberRepository
                .findByUser_IdAndGuild_IdAndStatus(userId, guildId, GuildMember.Status.ACTIVE)
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
        if (!guild.getMaster().getId().equals(requesterId)) {
            throw new CustomException(ErrorCode.NOT_GUILD_MASTER);
        }
    }

    private GuildListResponse.GuildSummary toGuildSummary(Guild guild) {
        List<Long> activeUserIds = guildMemberRepository.findActiveUserIdsByGuildId(guild.getId());
        return new GuildListResponse.GuildSummary(
                guild.getId(),
                guild.getName(),
                guild.getMaster().getNickname(),
                activeUserIds.size(),
                guild.getMaxMembers(),
                sumTrophyPoints(activeUserIds),
                countTerritories(activeUserIds),
                guild.getRecruitingStatus().name());
    }

    private long sumTrophyPoints(List<Long> userIds) {
        return userIds.isEmpty() ? 0 : userTrophyRepository.sumScoreByUserIdIn(userIds);
    }

    private long countTerritories(List<Long> userIds) {
        return userIds.isEmpty() ? 0 : territoryRepository.countByOwner_IdIn(userIds);
    }
}
