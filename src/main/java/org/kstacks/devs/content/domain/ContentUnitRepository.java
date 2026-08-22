package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentUnitRepository extends JpaRepository<ContentUnitEntity, UUID> {}
