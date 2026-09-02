package com.territorial.combat.internal.admin;

import org.springframework.data.jpa.repository.JpaRepository;

interface CombatCommandRepository extends JpaRepository<CombatCommand, String> {}
