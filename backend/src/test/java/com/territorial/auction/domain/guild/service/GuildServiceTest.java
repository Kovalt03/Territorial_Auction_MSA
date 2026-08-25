package com.territorial.auction.domain.guild.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.guild.dto.CreateGuildRequest;
import com.territorial.auction.domain.guild.dto.CreateGuildResponse;
import com.territorial.auction.domain.guild.dto.GuildApplicationListResponse;
import com.territorial.auction.domain.guild.dto.GuildDetailResponse;
import com.territorial.auction.domain.guild.dto.GuildListResponse;
import com.territorial.auction.domain.guild.dto.JoinGuildRequest;
import com.territorial.auction.domain.guild.dto.MyGuildResponse;
import com.territorial.auction.domain.guild.entity.Guild;
import com.territorial.auction.domain.guild.entity.GuildMember;
import com.territorial.auction.domain.guild.repository.GuildMemberRepository;
import com.territorial.auction.domain.guild.repository.GuildRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.season.repository.UserTrophyRepository;
import com.territorial.auction.domain.social.entity.ChatRoom;
import com.territorial.auction.domain.social.repository.ChatRoomRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuildServiceTest {

    @InjectMocks private GuildService guildService;

    @Mock private GuildRepository guildRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private UserTrophyRepository userTrophyRepository;
    @Mock private ChatRoomRepository chatRoomRepository;

    private User user;
    private User master;
    private Guild guild;
    private GuildMember masterMember;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("hashed")
                        .nickname("일반유저")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        master =
                User.builder()
                        .username("master1")
                        .email("master1@test.com")
                        .passwordHash("hashed")
                        .nickname("길드장")
                        .build();
        ReflectionTestUtils.setField(master, "id", 2L);

        guild =
                Guild.builder()
                        .name("정복자들")
                        .description("영토 정복을 목표로 하는 길드입니다.")
                        .emblem(null)
                        .master(master)
                        .build();
        ReflectionTestUtils.setField(guild, "id", 10L);
        ReflectionTestUtils.setField(guild, "maxMembers", 30);
        ReflectionTestUtils.setField(guild, "recruitingStatus", Guild.RecruitingStatus.OPEN);

        masterMember =
                GuildMember.builder()
                        .guild(guild)
                        .user(master)
                        .role(GuildMember.Role.MASTER)
                        .status(GuildMember.Status.ACTIVE)
                        .message(null)
                        .build();
        ReflectionTestUtils.setField(masterMember, "id", 100L);
    }

    // ─── createGuild() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createGuild()")
    class CreateGuild {

        @Test
        @DisplayName("성공 — 미소속 유저, 미중복 이름 → memberCount=1인 CreateGuildResponse 반환")
        void createGuild_success() {
            given(
                            guildMemberRepository.existsByUser_IdAndStatusIn(
                                    1L,
                                    List.of(GuildMember.Status.ACTIVE, GuildMember.Status.PENDING)))
                    .willReturn(false);
            given(guildRepository.existsByName("신규길드")).willReturn(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(guildRepository.save(any(Guild.class))).willReturn(guild);
            given(guildMemberRepository.save(any(GuildMember.class))).willReturn(masterMember);
            given(chatRoomRepository.save(any(ChatRoom.class)))
                    .willReturn(
                            ChatRoom.builder()
                                    .type(ChatRoom.ChatRoomType.GUILD)
                                    .targetId(10L)
                                    .build());

            CreateGuildRequest request = new CreateGuildRequest("신규길드", "설명", null);
            CreateGuildResponse response = guildService.createGuild(1L, request);

            assertThat(response.memberCount()).isEqualTo(1);
            assertThat(response.guildId()).isEqualTo(10L);
            assertThat(response.masterId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("이미 길드 소속 → ALREADY_IN_GUILD")
        void createGuild_alreadyInGuild() {
            given(
                            guildMemberRepository.existsByUser_IdAndStatusIn(
                                    1L,
                                    List.of(GuildMember.Status.ACTIVE, GuildMember.Status.PENDING)))
                    .willReturn(true);

            assertThatThrownBy(
                            () ->
                                    guildService.createGuild(
                                            1L, new CreateGuildRequest("신규길드", null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_IN_GUILD);
        }

        @Test
        @DisplayName("길드명 중복 → GUILD_NAME_DUPLICATED")
        void createGuild_nameDuplicated() {
            given(
                            guildMemberRepository.existsByUser_IdAndStatusIn(
                                    1L,
                                    List.of(GuildMember.Status.ACTIVE, GuildMember.Status.PENDING)))
                    .willReturn(false);
            given(guildRepository.existsByName("정복자들")).willReturn(true);

            assertThatThrownBy(
                            () ->
                                    guildService.createGuild(
                                            1L, new CreateGuildRequest("정복자들", null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GUILD_NAME_DUPLICATED);
        }
    }

    // ─── getGuilds() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getGuilds()")
    class GetGuilds {

        @Test
        @DisplayName("검색어 없음 → findAllWithMaster 호출")
        void getGuilds_noSearch() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Guild> page = new PageImpl<>(List.of(guild), pageable, 1);
            given(guildRepository.findAllWithMaster(pageable)).willReturn(page);
            given(guildMemberRepository.findActiveUserIdsByGuildId(10L)).willReturn(List.of(2L));
            given(userTrophyRepository.sumScoreByUserIdIn(List.of(2L))).willReturn(500L);
            given(territoryRepository.countByOwner_IdIn(List.of(2L))).willReturn(3L);

            GuildListResponse response = guildService.getGuilds(null, pageable);

            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.guilds()).hasSize(1);
            then(guildRepository).should().findAllWithMaster(pageable);
        }

        @Test
        @DisplayName("검색어 있음 → findByNameContainingWithMaster 호출")
        void getGuilds_withSearch() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Guild> page = new PageImpl<>(List.of(guild), pageable, 1);
            given(guildRepository.findByNameContainingWithMaster("정복", pageable)).willReturn(page);
            given(guildMemberRepository.findActiveUserIdsByGuildId(10L)).willReturn(List.of(2L));
            given(userTrophyRepository.sumScoreByUserIdIn(List.of(2L))).willReturn(500L);
            given(territoryRepository.countByOwner_IdIn(List.of(2L))).willReturn(3L);

            GuildListResponse response = guildService.getGuilds("정복", pageable);

            then(guildRepository).should().findByNameContainingWithMaster("정복", pageable);
            assertThat(response.guilds()).hasSize(1);
        }
    }

    // ─── getGuildDetail() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getGuildDetail()")
    class GetGuildDetail {

        @Test
        @DisplayName("성공 → GuildDetailResponse 반환")
        void getGuildDetail_success() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByGuildIdAndStatusWithUser(
                                    10L, GuildMember.Status.ACTIVE))
                    .willReturn(List.of(masterMember));
            // 배치 쿼리: countGroupByOwnerIds([2], OCCUPIED) → [[2, 2]]
            given(
                            territoryRepository.countGroupByOwnerIds(
                                    List.of(2L), Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(new Object[] {2L, 2L}));

            GuildDetailResponse response = guildService.getGuildDetail(10L);

            assertThat(response.guildId()).isEqualTo(10L);
            assertThat(response.members()).hasSize(1);
            assertThat(response.totalTerritoryCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("배치 쿼리 사용 — countGroupByOwnerIds 1회 호출, 멤버별 개별 쿼리 미호출")
        void getGuildDetail_usesBatchQueryForTerritoryCount() {
            GuildMember member2 =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.ACTIVE)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByGuildIdAndStatusWithUser(
                                    10L, GuildMember.Status.ACTIVE))
                    .willReturn(List.of(masterMember, member2));
            List<Long> memberUserIds = List.of(2L, 1L);
            given(
                            territoryRepository.countGroupByOwnerIds(
                                    memberUserIds, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(List.<Object[]>of(new Object[] {2L, 3L}, new Object[] {1L, 1L}));

            guildService.getGuildDetail(10L);

            // 배치 쿼리 1회
            then(territoryRepository)
                    .should()
                    .countGroupByOwnerIds(memberUserIds, Territory.TerritoryStatus.OCCUPIED);
            // 멤버별 개별 쿼리 미호출
            then(territoryRepository).should(never()).countByOwnerId(2L);
            then(territoryRepository).should(never()).countByOwnerId(1L);
        }

        @Test
        @DisplayName("존재하지 않는 길드 → GUILD_NOT_FOUND")
        void getGuildDetail_notFound() {
            given(guildRepository.findByIdWithMaster(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.getGuildDetail(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GUILD_NOT_FOUND);
        }
    }

    // ─── getMyGuild() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyGuild()")
    class GetMyGuild {

        @Test
        @DisplayName("성공 → MyGuildResponse 반환")
        void getMyGuild_success() {
            GuildMember activeMember =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.ACTIVE)
                            .message(null)
                            .build();
            given(guildMemberRepository.findByUser_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(activeMember));
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(guildMemberRepository.findActiveUserIdsByGuildId(10L))
                    .willReturn(List.of(1L, 2L));
            given(territoryRepository.countByOwner_IdIn(List.of(1L, 2L))).willReturn(5L);
            given(userTrophyRepository.sumScoreByUserIdIn(List.of(1L, 2L))).willReturn(1000L);

            MyGuildResponse response = guildService.getMyGuild(1L);

            assertThat(response.guildId()).isEqualTo(10L);
            assertThat(response.myRole()).isEqualTo("MEMBER");
            assertThat(response.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("길드 미소속 → NOT_IN_GUILD")
        void getMyGuild_notInGuild() {
            given(guildMemberRepository.findByUser_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.getMyGuild(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_IN_GUILD);
        }
    }

    // ─── joinGuild() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("joinGuild()")
    class JoinGuild {

        @Test
        @DisplayName("성공 → GuildMember PENDING으로 저장")
        void joinGuild_success() {
            given(guildRepository.findById(10L)).willReturn(Optional.of(guild));
            given(guildMemberRepository.existsByUser_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                    .willReturn(false);
            given(
                            guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(guildMemberRepository.save(any(GuildMember.class))).willReturn(masterMember);

            guildService.joinGuild(1L, 10L, new JoinGuildRequest("열심히 하겠습니다!"));

            then(guildMemberRepository).should().save(any(GuildMember.class));
        }

        @Test
        @DisplayName("길드 없음 → GUILD_NOT_FOUND")
        void joinGuild_guildNotFound() {
            given(guildRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.joinGuild(1L, 999L, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GUILD_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 ACTIVE 길드 소속 → ALREADY_IN_GUILD")
        void joinGuild_alreadyInGuild() {
            given(guildRepository.findById(10L)).willReturn(Optional.of(guild));
            given(guildMemberRepository.existsByUser_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                    .willReturn(true);

            assertThatThrownBy(() -> guildService.joinGuild(1L, 10L, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_IN_GUILD);
        }

        @Test
        @DisplayName("이미 PENDING 신청 → ALREADY_APPLIED")
        void joinGuild_alreadyApplied() {
            given(guildRepository.findById(10L)).willReturn(Optional.of(guild));
            given(guildMemberRepository.existsByUser_IdAndStatus(1L, GuildMember.Status.ACTIVE))
                    .willReturn(false);
            given(
                            guildMemberRepository.existsByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(true);

            assertThatThrownBy(() -> guildService.joinGuild(1L, 10L, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_APPLIED);
        }
    }

    // ─── approveApplication() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("approveApplication()")
    class ApproveApplication {

        @Test
        @DisplayName("성공 → 신청 ACTIVE로 변경")
        void approveApplication_success() {
            GuildMember application =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(Optional.of(application));
            given(guildMemberRepository.countByGuild_IdAndStatus(10L, GuildMember.Status.ACTIVE))
                    .willReturn(5L);

            guildService.approveApplication(2L, 10L, 1L);

            assertThat(application.getStatus()).isEqualTo(GuildMember.Status.ACTIVE);
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void approveApplication_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            // user(id=1)는 길드장(master id=2)이 아님
            assertThatThrownBy(() -> guildService.approveApplication(1L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }

        @Test
        @DisplayName("길드 정원 초과 → GUILD_FULL")
        void approveApplication_guildFull() {
            GuildMember application =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(Optional.of(application));
            given(guildMemberRepository.countByGuild_IdAndStatus(10L, GuildMember.Status.ACTIVE))
                    .willReturn(30L);

            assertThatThrownBy(() -> guildService.approveApplication(2L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GUILD_FULL);
        }

        @Test
        @DisplayName("신청서 없음 → APPLICATION_NOT_FOUND")
        void approveApplication_applicationNotFound() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.approveApplication(2L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ─── getApplications() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getApplications()")
    class GetApplications {

        @Test
        @DisplayName("성공 → 신청 목록 반환 (sumScoreGroupByUserIds 배치 쿼리 사용)")
        void getApplications_success() {
            GuildMember application =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message("열심히 하겠습니다!")
                            .build();
            ReflectionTestUtils.setField(application, "id", 200L);
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByGuildIdAndStatusWithUser(
                                    10L, GuildMember.Status.PENDING))
                    .willReturn(List.of(application));
            // 배치 쿼리: sumScoreGroupByUserIds([1]) → [[1, 1200]]
            given(userTrophyRepository.sumScoreGroupByUserIds(List.of(1L)))
                    .willReturn(List.<Object[]>of(new Object[] {1L, 1200L}));

            GuildApplicationListResponse response = guildService.getApplications(2L, 10L);

            assertThat(response.guildId()).isEqualTo(10L);
            assertThat(response.applications()).hasSize(1);
            assertThat(response.applications().get(0).trophyPoints()).isEqualTo(1200);
        }

        @Test
        @DisplayName("배치 쿼리 사용 — sumScoreGroupByUserIds 1회 호출, 개별 합산 쿼리 미호출")
        void getApplications_usesBatchQueryForTrophyScore() {
            GuildMember app1 =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message(null)
                            .build();
            ReflectionTestUtils.setField(app1, "id", 201L);
            GuildMember app2 =
                    GuildMember.builder()
                            .guild(guild)
                            .user(master)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message(null)
                            .build();
            ReflectionTestUtils.setField(app2, "id", 202L);

            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByGuildIdAndStatusWithUser(
                                    10L, GuildMember.Status.PENDING))
                    .willReturn(List.of(app1, app2));
            List<Long> applicantUserIds = List.of(1L, 2L);
            given(userTrophyRepository.sumScoreGroupByUserIds(applicantUserIds))
                    .willReturn(
                            List.<Object[]>of(new Object[] {1L, 500L}, new Object[] {2L, 800L}));

            guildService.getApplications(2L, 10L);

            // 배치 쿼리 1회
            then(userTrophyRepository).should().sumScoreGroupByUserIds(applicantUserIds);
            // 개별 합산 쿼리 미호출
            then(userTrophyRepository).should(never()).sumScoreByUserIdIn(List.of(1L));
            then(userTrophyRepository).should(never()).sumScoreByUserIdIn(List.of(2L));
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void getApplications_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            // user(id=1)는 길드장(master id=2)이 아님
            assertThatThrownBy(() -> guildService.getApplications(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }
    }

    // ─── rejectApplication() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectApplication()")
    class RejectApplication {

        @Test
        @DisplayName("성공 → 신청 CANCELLED로 변경")
        void rejectApplication_success() {
            GuildMember application =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.PENDING)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(Optional.of(application));

            guildService.rejectApplication(2L, 10L, 1L);

            assertThat(application.getStatus()).isEqualTo(GuildMember.Status.CANCELLED);
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void rejectApplication_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            assertThatThrownBy(() -> guildService.rejectApplication(1L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }

        @Test
        @DisplayName("PENDING 신청 없음 → APPLICATION_NOT_FOUND")
        void rejectApplication_applicationNotFound() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.PENDING))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.rejectApplication(2L, 10L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    // ─── transferMaster() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("transferMaster()")
    class TransferMaster {

        @Test
        @DisplayName("성공 → 길드 master 변경, 기존 길드장 MEMBER로, 신규 길드장 MASTER로")
        void transferMaster_success() {
            GuildMember newMasterMember =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.ACTIVE)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    2L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(masterMember));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(newMasterMember));

            guildService.transferMaster(
                    2L,
                    10L,
                    new com.territorial.auction.domain.guild.dto.TransferMasterRequest(1L));

            assertThat(masterMember.getRole()).isEqualTo(GuildMember.Role.MEMBER);
            assertThat(newMasterMember.getRole()).isEqualTo(GuildMember.Role.MASTER);
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void transferMaster_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            assertThatThrownBy(
                            () ->
                                    guildService.transferMaster(
                                            1L,
                                            10L,
                                            new com.territorial.auction.domain.guild.dto
                                                    .TransferMasterRequest(3L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }

        @Test
        @DisplayName("자기 자신에게 이전 → CANNOT_TRANSFER_TO_SELF")
        void transferMaster_toSelf() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            assertThatThrownBy(
                            () ->
                                    guildService.transferMaster(
                                            2L,
                                            10L,
                                            new com.territorial.auction.domain.guild.dto
                                                    .TransferMasterRequest(2L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }

        @Test
        @DisplayName("대상이 ACTIVE 멤버 아님 → NOT_IN_GUILD")
        void transferMaster_targetNotInGuild() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    2L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(masterMember));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    99L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    guildService.transferMaster(
                                            2L,
                                            10L,
                                            new com.territorial.auction.domain.guild.dto
                                                    .TransferMasterRequest(99L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_IN_GUILD);
        }
    }

    // ─── kickMember() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("kickMember()")
    class KickMember {

        @Test
        @DisplayName("성공 → 멤버 status KICKED로 변경")
        void kickMember_success() {
            GuildMember target =
                    GuildMember.builder()
                            .guild(guild)
                            .user(user)
                            .role(GuildMember.Role.MEMBER)
                            .status(GuildMember.Status.ACTIVE)
                            .message(null)
                            .build();
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    1L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(target));

            guildService.kickMember(2L, 10L, 1L);

            assertThat(target.getStatus()).isEqualTo(GuildMember.Status.KICKED);
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void kickMember_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            assertThatThrownBy(() -> guildService.kickMember(1L, 10L, 3L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }

        @Test
        @DisplayName("길드장 추방 시도 → CANNOT_KICK_MASTER")
        void kickMember_cannotKickMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    2L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.of(masterMember));

            assertThatThrownBy(() -> guildService.kickMember(2L, 10L, 2L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_KICK_MASTER);
        }

        @Test
        @DisplayName("해당 멤버 없음 → NOT_IN_GUILD")
        void kickMember_memberNotFound() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));
            given(
                            guildMemberRepository.findByUser_IdAndGuild_IdAndStatus(
                                    99L, 10L, GuildMember.Status.ACTIVE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> guildService.kickMember(2L, 10L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_IN_GUILD);
        }
    }

    // ─── updateGuild() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateGuild()")
    class UpdateGuild {

        @Test
        @DisplayName("성공 — 모든 필드 변경")
        void updateGuild_success_allFields() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            guildService.updateGuild(
                    2L,
                    10L,
                    new com.territorial.auction.domain.guild.dto.UpdateGuildRequest(
                            "새 소개글", "https://cdn.example.com/002.png", "CLOSED"));

            assertThat(guild.getDescription()).isEqualTo("새 소개글");
            assertThat(guild.getEmblem()).isEqualTo("https://cdn.example.com/002.png");
            assertThat(guild.getRecruitingStatus()).isEqualTo(Guild.RecruitingStatus.CLOSED);
        }

        @Test
        @DisplayName("성공 — null 필드는 기존 값 유지")
        void updateGuild_success_partialUpdate() {
            ReflectionTestUtils.setField(guild, "description", "기존 소개글");
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            guildService.updateGuild(
                    2L,
                    10L,
                    new com.territorial.auction.domain.guild.dto.UpdateGuildRequest(
                            null, null, "CLOSED"));

            assertThat(guild.getDescription()).isEqualTo("기존 소개글");
            assertThat(guild.getRecruitingStatus()).isEqualTo(Guild.RecruitingStatus.CLOSED);
        }

        @Test
        @DisplayName("길드장 아님 → NOT_GUILD_MASTER")
        void updateGuild_notGuildMaster() {
            given(guildRepository.findByIdWithMaster(10L)).willReturn(Optional.of(guild));

            assertThatThrownBy(
                            () ->
                                    guildService.updateGuild(
                                            1L,
                                            10L,
                                            new com.territorial.auction.domain.guild.dto
                                                    .UpdateGuildRequest("설명", null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GUILD_MASTER);
        }
    }
}
