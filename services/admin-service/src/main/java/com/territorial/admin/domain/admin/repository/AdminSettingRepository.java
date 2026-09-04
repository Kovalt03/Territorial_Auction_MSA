package com.territorial.admin.domain.admin.repository;

import com.territorial.admin.domain.admin.entity.AdminSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSettingRepository extends JpaRepository<AdminSetting, Long> {

    Optional<AdminSetting> findBySettingKey(String settingKey);
}
