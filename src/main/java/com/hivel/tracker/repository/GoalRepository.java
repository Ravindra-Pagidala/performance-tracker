package com.hivel.tracker.repository;

import com.hivel.tracker.entity.Goal;
import com.hivel.tracker.enums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByEmployeeId(UUID employeeId);

    List<Goal> findByEmployeeIdAndCycleId(UUID employeeId, UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, GoalStatus status);

    long countByEmployeeIdAndCycleIdAndStatus(UUID employeeId, UUID cycleId, GoalStatus status);

    @Query("""
        SELECT g
        FROM Goal g
        WHERE g.cycle.id = :cycleId
        ORDER BY g.updatedAt DESC
    """)
    List<Goal> findGoalsByCycleId(UUID cycleId);
}