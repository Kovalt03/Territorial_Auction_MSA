package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.UserProfile;
import com.territorial.auction.domain.user.repository.UserProfileRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProjectionService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void bootstrap(Long userId, String username, String email, String nickname) {
        insertUserProjection(userId, username, email, nickname);
        User user = userRepository.getReferenceById(userId);
        if (!userProfileRepository.existsById(userId)) {
            userProfileRepository.save(UserProfile.builder().user(user).build());
        }
    }

    @Transactional
    public void updateProjectedNickname(Long userId, String nickname) {
        jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", nickname, userId);
    }

    @Transactional
    public void updateProjectedStatus(Long userId, String status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
    }

    private void insertUserProjection(Long userId, String username, String email, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, password_hash, nickname, status, role) "
                        + "VALUES (?, ?, ?, '!', ?, 'ACTIVE', 'USER') ON CONFLICT (id) DO NOTHING",
                userId,
                username,
                email,
                nickname);
        Integer matches =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ? AND username = ? AND email = ?",
                        Integer.class,
                        userId,
                        username,
                        email);
        if (matches == null || matches != 1) {
            throw new IllegalStateException("User projection ID collision: userId=" + userId);
        }
    }
}
