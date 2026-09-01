package com.territorial.user.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOutboxEventRepository extends JpaRepository<UserOutboxEvent, String> {
    List<UserOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
