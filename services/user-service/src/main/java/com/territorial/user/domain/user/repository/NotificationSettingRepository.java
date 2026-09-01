package com.territorial.user.domain.user.repository;

import com.territorial.user.domain.user.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {}
