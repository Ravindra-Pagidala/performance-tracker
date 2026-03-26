package com.hivel.tracker.repository;

import com.hivel.tracker.entity.Employee;
import com.hivel.tracker.entity.EmployeeCycleStats;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndIsActiveTrue(UUID id);

    boolean existsByIdAndIsActiveTrue(UUID id);

    @EntityGraph(attributePaths = {})
    List<Employee> findByDepartmentIgnoreCaseAndIsActiveTrue(String department, Sort sort);

    @Query("""
        SELECT e
        FROM Employee e
        JOIN EmployeeCycleStats ecs ON ecs.employee.id = e.id
        WHERE e.isActive = true
          AND LOWER(e.department) = LOWER(:department)
          AND ecs.avgRating >= :minRating
        ORDER BY ecs.avgRating DESC, e.name ASC
    """)
    List<Employee> findEmployeesByDepartmentAndMinRating(
        @Param("department") String department,
        @Param("minRating") java.math.BigDecimal minRating
    );

    @Query("""
        SELECT e
        FROM Employee e
        WHERE e.isActive = true
        ORDER BY e.createdAt DESC
    """)
    List<Employee> findAllActiveEmployees();
}