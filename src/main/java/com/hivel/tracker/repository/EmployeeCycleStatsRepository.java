package com.hivel.tracker.repository;

import com.hivel.tracker.entity.EmployeeCycleStats;
import com.hivel.tracker.entity.EmployeeCycleStats.EmployeeCycleStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeCycleStatsRepository
    extends JpaRepository<EmployeeCycleStats, EmployeeCycleStatsId> {

    Optional<EmployeeCycleStats> findByIdEmployeeIdAndIdCycleId(UUID employeeId, UUID cycleId);

    List<EmployeeCycleStats> findByIdCycleIdOrderByAvgRatingDesc(UUID cycleId);

    @Query("""
        SELECT ecs
        FROM EmployeeCycleStats ecs
        JOIN FETCH ecs.employee e
        JOIN FETCH ecs.cycle c
        WHERE c.id = :cycleId
        ORDER BY ecs.avgRating DESC, e.name ASC
    """)
    List<EmployeeCycleStats> findTopPerformersByCycle(UUID cycleId);

    @Query("""
        SELECT AVG(ecs.avgRating)
        FROM EmployeeCycleStats ecs
        WHERE ecs.cycle.id = :cycleId
    """)
    Double findAverageRatingForCycle(UUID cycleId);

    @Query("""
        SELECT COALESCE(SUM(ecs.goalsCompleted), 0)
        FROM EmployeeCycleStats ecs
        WHERE ecs.cycle.id = :cycleId
    """)
    Integer sumGoalsCompletedByCycle(UUID cycleId);

    @Query("""
        SELECT COALESCE(SUM(ecs.goalsMissed), 0)
        FROM EmployeeCycleStats ecs
        WHERE ecs.cycle.id = :cycleId
    """)
    Integer sumGoalsMissedByCycle(UUID cycleId);
}