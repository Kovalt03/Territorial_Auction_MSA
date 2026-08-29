package com.territorial.auction.domain.building.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.building.repository.IslandGradeRepository;
import com.territorial.auction.domain.user.entity.NotificationSetting;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.repository.NotificationSettingRepository;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserBootstrapServiceTest {

    private static final long USER_ID = 1_000_000_000L;

    @InjectMocks private UserBootstrapService userBootstrapService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private IslandGradeRepository islandGradeRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    @Test
    void replayCreatesOnlyMissingUserProjections() {
        User user =
                User.builder()
                        .username("tester")
                        .email("test@example.com")
                        .passwordHash("!")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        HomeIsland island = HomeIsland.builder().user(user).build();
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .willReturn(1);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(notificationSettingRepository.existsById(USER_ID)).willReturn(false);
        given(userProfileRepository.existsById(USER_ID)).willReturn(false);
        given(homeIslandRepository.findByUserId(USER_ID)).willReturn(Optional.of(island));

        userBootstrapService.bootstrap(USER_ID, "tester", "test@example.com", "테스터");

        verify(notificationSettingRepository).save(any(NotificationSetting.class));
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(islandGradeRepository, never()).findByName(anyString());
        verify(buildingInstanceRepository, never()).save(any());
    }

    @Test
    void conflictingProjectionIdStopsBootstrap() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .willReturn(0);

        assertThatThrownBy(
                        () ->
                                userBootstrapService.bootstrap(
                                        USER_ID, "tester", "test@example.com", "테스터"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ID collision");

        verify(userRepository, never()).getReferenceById(USER_ID);
    }
}
