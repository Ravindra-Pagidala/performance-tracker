package com.hivel.tracker.controller;

import com.hivel.tracker.dto.request.SubmitReviewRequest;
import com.hivel.tracker.dto.response.ApiResponse;
import com.hivel.tracker.dto.response.ReviewResponse;
import com.hivel.tracker.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
        @RequestBody SubmitReviewRequest request
    ) {
        log.info("Received request to submit review — employeeId: {}, cycleId: {}, reviewerId: {}",
            request != null ? request.getEmployeeId() : null,
            request != null ? request.getCycleId() : null,
            request != null ? request.getReviewerId() : null);

        ReviewResponse response = reviewService.submitReview(request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Performance review submitted successfully",
                response
            ));
    }
}