package com.hivel.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(
    name = "employees",
    indexes = {
        @Index(name = "idx_employees_department",
               columnList = "department"),
        @Index(name = "idx_employees_department_active",
               columnList = "department, is_active")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    private static final Logger log = LoggerFactory.getLogger(Employee.class);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "role", nullable = false, length = 100)
    private String role;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    @OneToMany(
        mappedBy = "employee",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<PerformanceReview> reviews = new ArrayList<>();

    @OneToMany(
        mappedBy = "employee",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<Goal> goals = new ArrayList<>();


    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("Employee entity pre-persist triggered — setting timestamps. "
            + "name: {}, department: {}", this.name, this.department);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
        log.debug("Employee entity pre-update triggered — updating updatedAt. "
            + "id: {}", this.id);
    }
}