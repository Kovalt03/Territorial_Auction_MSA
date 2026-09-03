package com.territorial.auction.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.user.client.OAuthProvisionResult;
import com.territorial.auction.domain.user.client.UserProvisioningClient;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.service.UserProjectionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @InjectMocks private CustomOAuth2UserService customOAuth2UserService;

    @Mock private UserRepository userRepository;
    @Mock private UserProvisioningClient userProvisioningClient;
    @Mock private UserProjectionService userProjectionService;

    private OAuth2UserInfo userInfo;

    @BeforeEach
    void setUp() {
        // Google OAuth2 응답을 흉내낸 최소 구현체
        userInfo =
                new OAuth2UserInfo(
                        java.util.Map.of(
                                "sub",
                                "google-uid-999",
                                "email",
                                "oauth@example.com",
                                "name",
                                "테스트유저")) {
                    @Override
                    public String getId() {
                        return "google-uid-999";
                    }

                    @Override
                    public String getName() {
                        return "테스트유저";
                    }

                    @Override
                    public String getEmail() {
                        return "oauth@example.com";
                    }
                };
    }

    // ─── saveOrUpdate() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveOrUpdate()")
    class SaveOrUpdate {

        @Test
        @DisplayName("신규 가입 → user-service 프로비저닝 위임 + 로컬 부트스트랩")
        void saveOrUpdate_newUser_provisionsAndBootstraps() {
            // given
            given(userRepository.findByUsername("google:google-uid-999"))
                    .willReturn(Optional.empty());
            given(
                            userProvisioningClient.provisionOAuth(
                                    eq("google:google-uid-999"),
                                    eq("oauth@example.com"),
                                    anyString()))
                    .willReturn(
                            new OAuthProvisionResult(
                                    1000000001L,
                                    "google:google-uid-999",
                                    "테스트유저_1234",
                                    "oauth@example.com"));
            User projected =
                    User.builder()
                            .username("google:google-uid-999")
                            .email("oauth@example.com")
                            .passwordHash("!")
                            .nickname("테스트유저_1234")
                            .build();
            ReflectionTestUtils.setField(projected, "id", 1000000001L);
            given(userRepository.findById(1000000001L)).willReturn(Optional.of(projected));

            // when
            User result = customOAuth2UserService.saveOrUpdate(userInfo, "google");

            // then
            assertThat(result.getId()).isEqualTo(1000000001L);
            then(userProjectionService)
                    .should()
                    .bootstrap(
                            eq(1000000001L),
                            eq("google:google-uid-999"),
                            eq("oauth@example.com"),
                            eq("테스트유저_1234"));
        }

        @Test
        @DisplayName("기존 로컬 프로젝션 있으면 프로비저닝/부트스트랩 없음")
        void saveOrUpdate_existingUser_noProvisioning() {
            // given
            User existingUser =
                    User.builder()
                            .username("google:google-uid-999")
                            .email("oauth@example.com")
                            .passwordHash("!")
                            .nickname("기존유저_5678")
                            .build();
            ReflectionTestUtils.setField(existingUser, "id", 1000000002L);
            given(userRepository.findByUsername("google:google-uid-999"))
                    .willReturn(Optional.of(existingUser));

            // when
            User result = customOAuth2UserService.saveOrUpdate(userInfo, "google");

            // then
            assertThat(result.getId()).isEqualTo(1000000002L);
            then(userProvisioningClient)
                    .should(never())
                    .provisionOAuth(anyString(), anyString(), anyString());
            then(userProjectionService)
                    .should(never())
                    .bootstrap(any(), anyString(), anyString(), anyString());
        }
    }
}
