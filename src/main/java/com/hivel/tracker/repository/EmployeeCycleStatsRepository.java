package com.hivel.tracker.repository;

import com.hivel.tracker.entity.EmployeeCycleStats;
import com.hivel.tracker.entity.EmployeeCycleStats.EmployeeCycleStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT ecs
    FROM EmployeeCycleStats ecs
    WHERE ecs.id.employeeId = :employeeId
      AND ecs.id.cycleId = :cycleId
""")
    Optional<EmployeeCycleStats> findByEmployeeIdAndCycleIdForUpdate(
            @Param("employeeId") UUID employeeId,
            @Param("cycleId") UUID cycleId
    );

}