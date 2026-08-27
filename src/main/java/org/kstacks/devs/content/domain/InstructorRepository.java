package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<InstructorEntity, UUID> {
    List<InstructorEntity> findAllByOrderByNameEnAsc();
}
