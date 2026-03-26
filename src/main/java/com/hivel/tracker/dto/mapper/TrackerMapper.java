package com.hivel.tracker.dto.mapper;

import com.hivel.tracker.dto.response.*;
import com.hivel.tracker.entity.*;

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

    public static EmployeeFilterResponse toEmployeeFilterResponse(EmployeeFilterProjection projection) {
        return EmployeeFilterResponse.builder()
                .employeeId(projection.getEmployeeId())
                .employeeName(projection.getName())
                .department(projection.getDepartment())
                .role(projection.getRole())
                .averageRating(
                        projection.getAverageRating() != null
                                ? Math.round(projection.getAverageRating().doubleValue() * 100.0) / 100.0
                                : 0.0
                )
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

    public static GoalResponse toGoalResponse(Goal goal) {
        return GoalResponse.builder()
                .goalId(goal.getId())
                .employeeId(goal.getEmployee().getId())
                .employeeName(goal.getEmployee().getName())
                .cycleId(goal.getCycle().getId())
                .cycleName(goal.getCycle().getName())
                .title(goal.getTitle())
                .status(goal.getStatus())
                .build();
    }

    private static double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}