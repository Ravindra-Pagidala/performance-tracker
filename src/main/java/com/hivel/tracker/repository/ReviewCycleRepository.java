package com.hivel.tracker.repository;

import com.hivel.tracker.entity.ReviewCycle;
import com.hivel.tracker.enums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

    Optional<ReviewCycle> findByNameIgnoreCase(String name);

    List<ReviewCycle> findByStatus(CycleStatus status);

    Optional<ReviewCycle> findByIdAndStatus(UUID id, CycleStatus status);

    boolean existsById(UUID id);
}