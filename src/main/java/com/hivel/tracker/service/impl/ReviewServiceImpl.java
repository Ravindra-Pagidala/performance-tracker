package com.hivel.tracker.service.impl;

import com.hivel.tracker.dto.mapper.TrackerMapper;
import com.hivel.tracker.dto.request.SubmitReviewRequest;
import com.hivel.tracker.dto.response.ReviewResponse;
import com.hivel.tracker.entity.Employee;
import com.hivel.tracker.entity.EmployeeCycleStats;
import com.hivel.tracker.entity.PerformanceReview;
import com.hivel.tracker.entity.ReviewCycle;
import com.hivel.tracker.exception.BusinessValidationException;
import com.hivel.tracker.exception.DuplicateResourceException;
import com.hivel.tracker.exception.ResourceNotFoundException;
import com.hivel.tracker.repository.EmployeeCycleStatsRepository;
import com.hivel.tracker.repository.EmployeeRepository;
import com.hivel.tracker.repository.PerformanceReviewRepository;
import com.hivel.tracker.repository.ReviewCycleRepository;
import com.hivel.tracker.service.ReviewService;
import com.hivel.tracker.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final EmployeeRepository employeeRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeCycleStatsRepository employeeCycleStatsRepository;

    @Override
    @Transactional
    public ReviewResponse submitReview(SubmitReviewRequest request) {
        log.info("Submitting review — employeeId: {}, cycleId: {}, reviewerId: {}, rating: {}",
            request.getEmployeeId(), request.getCycleId(), request.getReviewerId(), request.getRating());

        ValidationUtils.requireNonNull(request, "SubmitReviewRequest");
        ValidationUtils.requireValidRating(request.getRating());

        UUID employeeId = ValidationUtils.requireValidUUID(request.getEmployeeId(), "employeeId");
        UUID cycleId = ValidationUtils.requireValidUUID(request.getCycleId(), "cycleId");
        UUID reviewerId = ValidationUtils.requireValidUUID(request.getReviewerId(), "reviewerId");

        ValidationUtils.requireNotSelfReview(employeeId, reviewerId);

        Employee employee = employeeRepository.findByIdAndIsActiveTrue(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Employee not found with id: " + employeeId
            ));

        Employee reviewer = employeeRepository.findByIdAndIsActiveTrue(reviewerId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Reviewer not found with id: " + reviewerId
            ));

        ReviewCycle cycle = reviewCycleRepository.findById(cycleId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Review cycle not found with id: " + cycleId
            ));

        if (!cycle.isOpen()) {
            throw new BusinessValidationException(
                "Cannot submit review. Review cycle is not OPEN: " + cycle.getName()
            );
        }

        boolean alreadyExists = performanceReviewRepository
            .existsByEmployeeIdAndCycleIdAndReviewerId(employeeId, cycleId, reviewerId);

        if (alreadyExists) {
            throw new DuplicateResourceException(
                "Review already exists for employeeId=" + employeeId +
                ", cycleId=" + cycleId +
                ", reviewerId=" + reviewerId
            );
        }

        PerformanceReview review = PerformanceReview.builder()
            .employee(employee)
            .cycle(cycle)
            .reviewer(reviewer)
            .rating(request.getRating())
            .notes(request.getNotes() != null ? request.getNotes().trim() : null)
            .build();

        PerformanceReview savedReview = performanceReviewRepository.save(review);

        cycle.addRating(savedReview.getRating());
        reviewCycleRepository.save(cycle);

        updateEmployeeCycleStats(employee, cycle, savedReview.getRating());

        log.info("Review submitted successfully — reviewId: {}", savedReview.getId());

        return TrackerMapper.toReviewResponse(savedReview);
    }

    private void updateEmployeeCycleStats(Employee employee, ReviewCycle cycle, Integer rating) {
        EmployeeCycleStats stats = employeeCycleStatsRepository
            .findByIdEmployeeIdAndIdCycleId(employee.getId(), cycle.getId())
            .orElseGet(() -> EmployeeCycleStats.builder()
                .id(new EmployeeCycleStats.EmployeeCycleStatsId(employee.getId(), cycle.getId()))
                .employee(employee)
                .cycle(cycle)
                .build());

        stats.addReview(rating);

        employeeCycleStatsRepository.save(stats);

        log.debug("Updated employee cycle stats — employeeId: {}, cycleId: {}",
            employee.getId(), cycle.getId());
    }
}