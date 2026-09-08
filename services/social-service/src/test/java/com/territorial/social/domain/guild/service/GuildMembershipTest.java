package com.territorial.social.domain.guild.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.social.client.MemberStatsClient;
import com.territorial.social.domain.guild.dto.JoinGuildRequest;
import com.territorial.social.domain.guild.entity.Guild;
import com.territorial.social.domain.guild.entity.GuildMember;
import com.territorial.social.domain.guild.repository.GuildMemberRepository;
import com.territorial.social.domain.guild.repository.GuildRepository;
import com.territorial.social.domain.social.repository.ChatRoomRepository;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import com.territorial.social.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuildMembershipTest {

    @InjectMocks private GuildService guildService;
    @Mock private GuildRepository guildRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private UserDisplayRepository userDisplayRepository;
    @Mock private MemberStatsClient memberStatsClient;

    private static final long GUILD_ID = 1L;
    private static final long MASTER_ID = 10L;

    private Guild guild(long masterId) {
        Guild g = Guild.builder().name("길드").masterId(masterId).build();
        ReflectionTestUtils.setField(g, "id", GUILD_ID);
        return g;
    }

    private GuildMember member(Guild g, long userId, GuildMember.Role role) {
        return GuildMember.builder()
                .guild(g)
                .userId(userId)
                .role(role)
                .status(GuildMember.Status.ACTIVE)
                .build();
    }

    @DisplayName("가입 신청 — 이미 다른 길드의 활성 멤버면 ALREADY_IN_GUILD")
    @Test
    void joinGuild_alreadyActiveMember_rejected() {
        given(guildRepository.findById(GUILD_ID)).willReturn(Optional.of(guild(99L)));
        given(guildMemberRepository.existsByUserIdAndStatus(20L, GuildMember.Status.ACTIVE))
                .willReturn(true);

        assertThatThrownBy(
                        () -> guildService.joinGuild(20L, GUILD_ID, new JoinGuildRequest("hi")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_IN_GUILD);
    }

    @DisplayName("추방 — 마스터가 일반 멤버를 추방하면 상태가 KICKED로 바뀐다")
    @Test
    void kickMember_byMaster_setsKicked() {
        Guild g = guild(MASTER_ID);
        GuildMember target = member(g, 20L, GuildMember.Role.MEMBER);
        given(guildRepository.findById(GUILD_ID)).willReturn(Optional.of(g));
        given(
                        guildMemberRepository.findByUserIdAndGuild_IdAndStatus(
                                20L, GUILD_ID, GuildMember.Status.ACTIVE))
                .willReturn(Optional.of(target));

        guildService.kickMember(MASTER_ID, GUILD_ID, 20L);

        assertThat(target.getStatus()).isEqualTo(GuildMember.Status.KICKED);
    }

    @DisplayName("추방 — 마스터가 아닌 요청자는 NOT_GUILD_MASTER")
    @Test
    void kickMember_byNonMaster_rejected() {
        given(guildRepository.findById(GUILD_ID)).willReturn(Optional.of(guild(MASTER_ID)));

        assertThatThrownBy(() -> guildService.kickMember(99L, GUILD_ID, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
    }

    @DisplayName("추방 — 대상이 마스터면 CANNOT_KICK_MASTER")
    @Test
    void kickMember_targetIsMaster_rejected() {
        Guild g = guild(MASTER_ID);
        GuildMember masterMember = member(g, MASTER_ID, GuildMember.Role.MASTER);
        given(guildRepository.findById(GUILD_ID)).willReturn(Optional.of(g));
        given(
                        guildMemberRepository.findByUserIdAndGuild_IdAndStatus(
                                MASTER_ID, GUILD_ID, GuildMember.Status.ACTIVE))
                .willReturn(Optional.of(masterMember));

        assertThatThrownBy(() -> guildService.kickMember(MASTER_ID, GUILD_ID, MASTER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_KICK_MASTER);
    }

    @DisplayName("탈퇴 — 마스터가 남은 멤버가 있는데 탈퇴하려 하면 GUILD_MASTER_CANNOT_LEAVE")
    @Test
    void leaveGuild_masterWithOtherMembers_rejected() {
        Guild g = guild(MASTER_ID);
        GuildMember masterMember = member(g, MASTER_ID, GuildMember.Role.MASTER);
        given(guildRepository.findById(GUILD_ID)).willReturn(Optional.of(g));
        given(
                        guildMemberRepository.findByUserIdAndGuild_IdAndStatus(
                                MASTER_ID, GUILD_ID, GuildMember.Status.ACTIVE))
                .willReturn(Optional.of(masterMember));
        given(guildMemberRepository.countByGuild_IdAndStatus(GUILD_ID, GuildMember.Status.ACTIVE))
                .willReturn(2L);

        assertThatThrownBy(() -> guildService.leaveGuild(MASTER_ID, GUILD_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GUILD_MASTER_CANNOT_LEAVE);
    }
}
