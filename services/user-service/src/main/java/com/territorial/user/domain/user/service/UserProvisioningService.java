package com.territorial.user.domain.user.service;

import com.territorial.user.domain.user.dto.OAuthProvisionResult;
import com.territorial.user.domain.user.entity.NotificationSetting;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.entity.Wallet;
import com.territorial.user.domain.user.repository.NotificationSettingRepository;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.domain.user.repository.WalletRepository;
import com.territorial.user.event.UserCreatedEvent;
import com.territorial.user.event.UserCreatedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth 로그인 시 user-service가 신원을 소유(역전)한다. 모놀리식이 이 계약을 동기 호출해 ID를 받아 로컬 프로젝션을 만든다. username(=provider:providerId)
 * 기준 멱등.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserCreatedEventPublisher userCreatedEventPublisher;

    @Transactional
    public OAuthProvisionResult provisionOAuth(String username, String email, String nickname) {
        return userRepository
                .findByUsername(username)
                .map(OAuthProvisionResult::from)
                .orElseGet(() -> createOAuthUser(username, email, nickname));
    }

    private OAuthProvisionResult createOAuthUser(String username, String email, String nickname) {
        User user =
                userRepository.save(
                        User.builder()
                                .username(username)
                                .email(email)
                                .passwordHash("")
                                .nickname(nickname)
                                .build());
        walletRepository.save(Wallet.builder().user(user).build());
        notificationSettingRepository.save(NotificationSetting.builder().user(user).build());
        userCreatedEventPublisher.enqueue(
                new UserCreatedEvent(user.getId(), username, email, nickname));
        return OAuthProvisionResult.from(user);
    }
}
