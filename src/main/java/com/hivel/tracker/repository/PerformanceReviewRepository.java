package com.hivel.tracker.repository;

import com.hivel.tracker.entity.PerformanceReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    boolean existsByEmployeeIdAndCycleIdAndReviewerId(
        UUID employeeId,
        UUID cycleId,
        UUID reviewerId
    );

    @EntityGraph(attributePaths = {"employee", "cycle", "reviewer"})
    List<PerformanceReview> findByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId);

    @EntityGraph(attributePaths = {"employee", "cycle", "reviewer"})
    List<PerformanceReview> findByEmployeeIdAndCycleIdOrderBySubmittedAtDesc(
        UUID employeeId,
        UUID cycleId
    );

    @Query("""
        SELECT pr
        FROM PerformanceReview pr
        WHERE pr.cycle.id = :cycleId
        ORDER BY pr.rating DESC, pr.submittedAt ASC
    """)
    List<PerformanceReview> findTopReviewsForCycle(@Param("cycleId") UUID cycleId);

    @Query("""
        SELECT COUNT(pr)
        FROM PerformanceReview pr
        WHERE pr.cycle.id = :cycleId
    """)
    long countByCycleIdCustom(@Param("cycleId") UUID cycleId);

    @Query("""
        SELECT pr
        FROM PerformanceReview pr
        JOIN FETCH pr.cycle c
        JOIN FETCH pr.reviewer r
        WHERE pr.employee.id = :employeeId
        ORDER BY pr.submittedAt DESC
    """)
    List<PerformanceReview> findEmployeeReviewsWithCycleAndReviewer(
        @Param("employeeId") UUID employeeId
    );
}