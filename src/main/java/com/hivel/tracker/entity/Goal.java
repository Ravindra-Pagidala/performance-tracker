package com.hivel.tracker.entity;

import com.hivel.tracker.enums.GoalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(
    name = "goals",
    indexes = {
        @Index(name = "idx_goals_cycle_status",
               columnList = "cycle_id, status"),
        @Index(name = "idx_goals_employee_cycle",
               columnList = "employee_id, cycle_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Goal {

    private static final Logger log = LoggerFactory.getLogger(Goal.class);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "employee_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_goals_employee")
    )
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "cycle_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_goals_cycle")
    )
    private ReviewCycle cycle;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GoalStatus status = GoalStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("Goal pre-persist — employeeId: {}, cycleId: {}, "
            + "title: {}, status: {}",
            this.employee != null ? this.employee.getId() : "null",
            this.cycle != null ? this.cycle.getId() : "null",
            this.title,
            this.status);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
        log.debug("Goal pre-update — id: {}, status: {}", this.id, this.status);
    }
}