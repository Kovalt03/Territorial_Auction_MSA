package com.territorial.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.user.domain.user.dto.OAuthProvisionResult;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.domain.user.repository.NotificationSettingRepository;
import com.territorial.user.event.UserCreatedEvent;
import com.territorial.user.event.UserCreatedEventPublisher;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @InjectMocks private UserProvisioningService userProvisioningService;
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private UserCreatedEventPublisher userCreatedEventPublisher;

    @Test
    void provisionOAuthCreatesUserWalletAndPublishesEvent() {
        given(userRepository.findByUsername("google:1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class)))
                .willAnswer(
                        invocation -> {
                            User saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 1000000001L);
                            return saved;
                        });

        OAuthProvisionResult result =
                userProvisioningService.provisionOAuth("google:1", "a@oauth", "닉_1234");

        assertThat(result.userId()).isEqualTo(1000000001L);
        assertThat(result.username()).isEqualTo("google:1");
        assertThat(result.nickname()).isEqualTo("닉_1234");
        verify(walletRepository).save(any(Wallet.class));
        verify(userCreatedEventPublisher).enqueue(any(UserCreatedEvent.class));
    }

    @Test
    void provisionOAuthIsIdempotentByUsername() {
        User existing =
                User.builder()
                        .username("google:1")
                        .email("a@oauth")
                        .passwordHash("")
                        .nickname("닉_1234")
                        .build();
        ReflectionTestUtils.setField(existing, "id", 1000000001L);
        given(userRepository.findByUsername("google:1")).willReturn(Optional.of(existing));

        OAuthProvisionResult result =
                userProvisioningService.provisionOAuth("google:1", "a@oauth", "닉_1234");

        assertThat(result.userId()).isEqualTo(1000000001L);
        verify(userRepository, never()).save(any(User.class));
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(userCreatedEventPublisher, never()).enqueue(any(UserCreatedEvent.class));
    }
}
