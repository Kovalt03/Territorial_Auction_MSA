package com.territorial.user.domain.user.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.user.domain.user.dto.ChangeNicknameResponse;
import com.territorial.user.domain.user.entity.User;
import com.territorial.user.domain.user.repository.UserRepository;
import com.territorial.user.event.UserUpdatedEvent;
import com.territorial.user.event.UserUpdatedEventPublisher;
import com.territorial.user.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신원 프로필 쓰기(닉네임·비밀번호). user-service가 User를 소유한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserUpdatedEventPublisher userUpdatedEventPublisher;

    @Transactional
    public ChangeNicknameResponse changeNickname(Long userId, String nickname) {
        User user = findUserOrThrow(userId);
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.updateNickname(nickname);
        // 닉네임은 모놀리식 프로젝션과 15개 도메인 표시에 쓰이므로 변경을 전파한다.
        userUpdatedEventPublisher.enqueue(new UserUpdatedEvent(user.getId(), nickname));
        return new ChangeNicknameResponse(user.getId(), user.getNickname(), LocalDateTime.now());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUserOrThrow(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
