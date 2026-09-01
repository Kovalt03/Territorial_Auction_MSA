package com.territorial.social.domain.guild.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.social.client.MemberStatsClient;
import com.territorial.social.domain.guild.dto.CreateGuildRequest;
import com.territorial.social.domain.guild.dto.CreateGuildResponse;
import com.territorial.social.domain.guild.dto.JoinGuildRequest;
import com.territorial.social.domain.guild.dto.TransferMasterRequest;
import com.territorial.social.domain.guild.entity.Guild;
import com.territorial.social.domain.guild.entity.GuildMember;
import com.territorial.social.domain.guild.repository.GuildMemberRepository;
import com.territorial.social.domain.guild.repository.GuildRepository;
import com.territorial.social.domain.social.entity.ChatRoom;
import com.territorial.social.domain.social.repository.ChatRoomRepository;
import com.territorial.social.domain.user.entity.UserDisplay;
import com.territorial.social.domain.user.repository.UserDisplayRepository;
import com.territorial.social.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuildServiceTest {

    @InjectMocks private GuildService guildService;
    @Mock private GuildRepository guildRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private UserDisplayRepository userDisplayRepository;
    @Mock private MemberStatsClient memberStatsClient;

    private Guild guild(long id, long masterId) {
        Guild g = Guild.builder().name("길드").masterId(masterId).build();
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    @Test
    void createGuild_success() {
        given(guildMemberRepository.existsByUserIdAndStatusIn(any(), any())).willReturn(false);
        given(guildRepository.existsByName("길드")).willReturn(false);
        given(guildRepository.save(any(Guild.class)))
                .willAnswer(
                        inv -> {
                            Guild g = inv.getArgument(0);
                            ReflectionTestUtils.setField(g, "id", 1L);
                            return g;
                        });
        given(userDisplayRepository.findById(10L))
                .willReturn(Optional.of(new UserDisplay(10L, "길드장")));

        CreateGuildResponse res =
                guildService.createGuild(10L, new CreateGuildRequest("길드", "설명", null));

        assertThat(res.guildId()).isEqualTo(1L);
        assertThat(res.masterId()).isEqualTo(10L);
        assertThat(res.masterNickname()).isEqualTo("길드장");
        verify(guildMemberRepository).save(any(GuildMember.class));
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void createGuild_duplicateName() {
        given(guildMemberRepository.existsByUserIdAndStatusIn(any(), any())).willReturn(false);
        given(guildRepository.existsByName("길드")).willReturn(true);

        assertThatThrownBy(
                        () ->
                                guildService.createGuild(
                                        10L, new CreateGuildRequest("길드", null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GUILD_NAME_DUPLICATED);
        verify(guildRepository, never()).save(any());
    }

    @Test
    void createGuild_alreadyInGuild() {
        given(guildMemberRepository.existsByUserIdAndStatusIn(any(), any())).willReturn(true);

        assertThatThrownBy(
                        () ->
                                guildService.createGuild(
                                        10L, new CreateGuildRequest("길드", null, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_IN_GUILD);
    }

    @Test
    void joinGuild_alreadyApplied() {
        given(guildRepository.findById(1L)).willReturn(Optional.of(guild(1L, 99L)));
        given(guildMemberRepository.existsByUserIdAndStatus(10L, GuildMember.Status.ACTIVE))
                .willReturn(false);
        given(
                        guildMemberRepository.existsByUserIdAndGuild_IdAndStatus(
                                10L, 1L, GuildMember.Status.PENDING))
                .willReturn(true);

        assertThatThrownBy(() -> guildService.joinGuild(10L, 1L, new JoinGuildRequest("m")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_APPLIED);
    }

    @Test
    void approveApplication_guildFull() {
        Guild g = guild(1L, 10L);
        ReflectionTestUtils.setField(g, "maxMembers", 1);
        given(guildRepository.findById(1L)).willReturn(Optional.of(g));
        GuildMember pending =
                GuildMember.builder()
                        .guild(g)
                        .userId(20L)
                        .role(GuildMember.Role.MEMBER)
                        .status(GuildMember.Status.PENDING)
                        .build();
        given(
                        guildMemberRepository.findByUserIdAndGuild_IdAndStatus(
                                20L, 1L, GuildMember.Status.PENDING))
                .willReturn(Optional.of(pending));
        given(guildMemberRepository.countByGuild_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                .willReturn(1L);

        assertThatThrownBy(() -> guildService.approveApplication(10L, 1L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GUILD_FULL);
    }

    @Test
    void transferMaster_toSelfRejected() {
        given(guildRepository.findById(1L)).willReturn(Optional.of(guild(1L, 10L)));

        assertThatThrownBy(
                        () -> guildService.transferMaster(10L, 1L, new TransferMasterRequest(10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_TRANSFER_TO_SELF);
    }

    @Test
    void getGuildDetail_resolvesNicknameAndStats() {
        Guild g = guild(1L, 10L);
        given(guildRepository.findById(1L)).willReturn(Optional.of(g));
        GuildMember master =
                GuildMember.builder()
                        .guild(g)
                        .userId(10L)
                        .role(GuildMember.Role.MASTER)
                        .status(GuildMember.Status.ACTIVE)
                        .build();
        given(
                        guildMemberRepository.findByGuild_IdAndStatusOrderByJoinedAtAsc(
                                1L, GuildMember.Status.ACTIVE))
                .willReturn(List.of(master));
        given(memberStatsClient.fetch(List.of(10L)))
                .willReturn(Map.of(10L, new MemberStatsClient.MemberStat(10L, 3L, 100L)));
        lenient()
                .when(userDisplayRepository.findAllById(any()))
                .thenReturn(List.of(new UserDisplay(10L, "길드장")));
        given(userDisplayRepository.findById(10L))
                .willReturn(Optional.of(new UserDisplay(10L, "길드장")));

        var detail = guildService.getGuildDetail(1L);

        assertThat(detail.master().nickname()).isEqualTo("길드장");
        assertThat(detail.totalTerritoryCount()).isEqualTo(3L);
        assertThat(detail.members().get(0).territoryCount()).isEqualTo(3L);
    }
}
