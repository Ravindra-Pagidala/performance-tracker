package com.hivel.tracker.entity;

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
    name = "performance_reviews",
    indexes = {
        @Index(name = "idx_reviews_employee_id",
               columnList = "employee_id"),
        @Index(name = "idx_reviews_cycle_id",
               columnList = "cycle_id"),
        @Index(name = "idx_reviews_cycle_rating",
               columnList = "cycle_id, rating DESC"),
        @Index(name = "idx_reviews_employee_cycle",
               columnList = "employee_id, cycle_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_review_per_reviewer_per_cycle",
            columnNames = {"employee_id", "cycle_id", "reviewer_id"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceReview {

    private static final Logger log =
        LoggerFactory.getLogger(PerformanceReview.class);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "employee_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_employee")
    )
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "cycle_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_cycle")
    )
    private ReviewCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reviewer_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_reviewer")
    )
    private Employee reviewer;

    @Column(
        name = "rating",
        nullable = false,
        columnDefinition = "SMALLINT"
    )
    private Short rating;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.submittedAt = now;
        this.updatedAt = now;
        log.debug("PerformanceReview pre-persist — employeeId: {}, "
            + "cycleId: {}, reviewerId: {}, rating: {}",
            this.employee != null ? this.employee.getId() : "null",
            this.cycle != null ? this.cycle.getId() : "null",
            this.reviewer != null ? this.reviewer.getId() : "null",
            this.rating);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
        log.debug("PerformanceReview pre-update — id: {}, rating: {}",
            this.id, this.rating);
    }
}