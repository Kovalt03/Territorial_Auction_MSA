package com.territorial.combat.internal.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "combat_commands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CombatCommand {

    @Id
    @Column(name = "command_key", length = 200)
    private String commandKey;

    @Column(name = "command_type", nullable = false, length = 100)
    private String commandType;

    @Column(name = "request_fingerprint", nullable = false, length = 500)
    private String requestFingerprint;

    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    CombatCommand(String commandKey, String commandType, String requestFingerprint) {
        this.commandKey = commandKey;
        this.commandType = commandType;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = LocalDateTime.now();
        this.completedAt = this.createdAt;
    }
}
