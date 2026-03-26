package com.hivel.tracker.repository;

import com.hivel.tracker.entity.ReviewCycle;
import com.hivel.tracker.enums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

    Optional<ReviewCycle> findByNameIgnoreCase(String name);

    List<ReviewCycle> findByStatus(CycleStatus status);

    Optional<ReviewCycle> findByIdAndStatus(UUID id, CycleStatus status);

    boolean existsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT rc
    FROM ReviewCycle rc
    WHERE rc.id = :cycleId
""")
    Optional<ReviewCycle> findByIdForUpdate(@Param("cycleId") UUID cycleId);
}