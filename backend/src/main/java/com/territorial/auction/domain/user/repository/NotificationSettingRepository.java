package com.territorial.auction.domain.user.repository;

import com.territorial.auction.domain.user.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {}
