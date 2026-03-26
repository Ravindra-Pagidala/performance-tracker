package com.hivel.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "employee_cycle_stats",
    indexes = {
        @Index(name = "idx_ecs_cycle_avg_rating",
               columnList = "cycle_id, avg_rating DESC"),
        @Index(name = "idx_ecs_employee_id",
               columnList = "employee_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCycleStats {

    private static final Logger log =
        LoggerFactory.getLogger(EmployeeCycleStats.class);

    @EmbeddedId
    private EmployeeCycleStatsId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("employeeId")
    @JoinColumn(
        name = "employee_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ecs_employee")
    )
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("cycleId")
    @JoinColumn(
        name = "cycle_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ecs_cycle")
    )
    private ReviewCycle cycle;

    @Column(
        name = "avg_rating",
        nullable = false,
        precision = 3,
        scale = 2
    )
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "total_rating", nullable = false)
    @Builder.Default
    private Integer totalRating = 0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "goals_completed", nullable = false)
    @Builder.Default
    private Integer goalsCompleted = 0;

    @Column(name = "goals_missed", nullable = false)
    @Builder.Default
    private Integer goalsMissed = 0;

    @Column(name = "goals_pending", nullable = false)
    @Builder.Default
    private Integer goalsPending = 0;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;


    public void addReview(int rating) {
        log.debug("EmployeeCycleStats.addReview — employeeId: {}, cycleId: {}, "
            + "newRating: {}, before: totalRating={}, reviewCount={}",
            this.id != null ? this.id.getEmployeeId() : "null",
            this.id != null ? this.id.getCycleId() : "null",
            rating, this.totalRating, this.reviewCount);

        this.totalRating += rating;
        this.reviewCount += 1;
        this.avgRating = computeAverage();
        this.lastUpdated = OffsetDateTime.now();

        log.debug("EmployeeCycleStats.addReview — after: totalRating={}, "
            + "reviewCount={}, avgRating={}",
            this.totalRating, this.reviewCount, this.avgRating);
    }

    private BigDecimal computeAverage() {
        if (this.reviewCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(this.totalRating)
            .divide(BigDecimal.valueOf(this.reviewCount), 2, RoundingMode.HALF_UP);
    }

    @PrePersist
    protected void onCreate() {
        this.lastUpdated = OffsetDateTime.now();
        log.debug("EmployeeCycleStats pre-persist — employeeId: {}, cycleId: {}",
            this.id != null ? this.id.getEmployeeId() : "null",
            this.id != null ? this.id.getCycleId() : "null");
    }


    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeCycleStatsId implements java.io.Serializable {

        @Column(name = "employee_id")
        private UUID employeeId;

        @Column(name = "cycle_id")
        private UUID cycleId;
    }
}