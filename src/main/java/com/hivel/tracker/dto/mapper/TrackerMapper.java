package com.hivel.tracker.dto.mapper;

import com.hivel.tracker.dto.response.*;
import com.hivel.tracker.entity.Employee;
import com.hivel.tracker.entity.EmployeeCycleStats;
import com.hivel.tracker.entity.PerformanceReview;
import com.hivel.tracker.entity.ReviewCycle;

public final class TrackerMapper {

    private TrackerMapper() {
        throw new UnsupportedOperationException("TrackerMapper is a utility class");
    }

    public static EmployeeResponse toEmployeeResponse(Employee employee) {
        return EmployeeResponse.builder()
            .id(employee.getId())
            .name(employee.getName())
            .department(employee.getDepartment())
            .role(employee.getRole())
            .joiningDate(employee.getJoiningDate())
            .isActive(employee.isActive())
            .createdAt(employee.getCreatedAt())
            .updatedAt(employee.getUpdatedAt())
            .build();
    }

    public static ReviewResponse toReviewResponse(PerformanceReview review) {
        return ReviewResponse.builder()
            .reviewId(review.getId())
            .employeeId(review.getEmployee().getId())
            .employeeName(review.getEmployee().getName())
            .cycleId(review.getCycle().getId())
            .cycleName(review.getCycle().getName())
            .reviewerId(review.getReviewer().getId())
            .reviewerName(review.getReviewer().getName())
            .rating(review.getRating())
            .notes(review.getNotes())
            .submittedAt(review.getSubmittedAt())
            .build();
    }

    public static EmployeeReviewDetailsResponse toEmployeeReviewDetailsResponse(
        PerformanceReview review
    ) {
        return EmployeeReviewDetailsResponse.builder()
            .reviewId(review.getId())
            .cycleId(review.getCycle().getId())
            .cycleName(review.getCycle().getName())
            .reviewerId(review.getReviewer().getId())
            .reviewerName(review.getReviewer().getName())
            .rating(review.getRating())
            .notes(review.getNotes())
            .submittedAt(review.getSubmittedAt())
            .build();
    }

    public static EmployeeFilterResponse toEmployeeFilterResponse(
        Employee employee,
        Double averageRating
    ) {
        return EmployeeFilterResponse.builder()
            .employeeId(employee.getId())
            .employeeName(employee.getName())
            .department(employee.getDepartment())
            .role(employee.getRole())
            .averageRating(averageRating)
            .build();
    }

    public static CycleSummaryResponse toCycleSummaryResponse(
        ReviewCycle cycle,
        Double averageRating,
        EmployeeCycleStats topPerformer,
        Integer completedGoals,
        Integer missedGoals
    ) {
        return CycleSummaryResponse.builder()
            .cycleId(cycle.getId())
            .cycleName(cycle.getName())
            .averageRating(averageRating != null ? round(averageRating) : 0.0)
            .topPerformerEmployeeId(
                topPerformer != null ? topPerformer.getEmployee().getId() : null
            )
            .topPerformerName(
                topPerformer != null ? topPerformer.getEmployee().getName() : null
            )
            .topPerformerAverageRating(
                topPerformer != null ? topPerformer.getAvgRating().doubleValue() : null
            )
            .completedGoals(completedGoals != null ? completedGoals : 0)
            .missedGoals(missedGoals != null ? missedGoals : 0)
            .build();
    }

    private static double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}