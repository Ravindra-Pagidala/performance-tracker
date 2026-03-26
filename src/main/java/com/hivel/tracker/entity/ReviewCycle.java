package com.hivel.tracker.entity;

import com.hivel.tracker.enums.CycleStatus;
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
    name = "review_cycles",
    indexes = {
        @Index(name = "idx_review_cycles_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCycle {

    private static final Logger log = LoggerFactory.getLogger(ReviewCycle.class);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CycleStatus status = CycleStatus.OPEN;

    @Column(name = "total_rating", nullable = false)
    @Builder.Default
    private Integer totalRating = 0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    @OneToMany(
        mappedBy = "cycle",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<PerformanceReview> reviews = new ArrayList<>();

    @OneToMany(
        mappedBy = "cycle",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<Goal> goals = new ArrayList<>();


    public void addRating(int rating) {
        log.debug("Adding rating {} to cycle '{}'. "
            + "Before: totalRating={}, reviewCount={}",
            rating, this.name, this.totalRating, this.reviewCount);

        this.totalRating += rating;
        this.reviewCount += 1;

        log.debug("After adding rating. totalRating={}, reviewCount={}, "
            + "newAverage={}", this.totalRating, this.reviewCount,
            this.getAverageRating());
    }


    public double getAverageRating() {
        if (this.reviewCount == 0) {
            return 0.0;
        }
        // Round to 2 decimal places
        return Math.round(((double) this.totalRating / this.reviewCount) * 100.0) / 100.0;
    }

    public boolean isOpen() {
        return CycleStatus.OPEN.equals(this.status);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        log.debug("ReviewCycle pre-persist — name: {}, status: {}",
            this.name, this.status);
    }
}