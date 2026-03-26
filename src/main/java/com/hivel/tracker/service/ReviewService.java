package com.hivel.tracker.service;

import com.hivel.tracker.dto.request.SubmitReviewRequest;
import com.hivel.tracker.dto.response.ReviewResponse;

public interface ReviewService {

    ReviewResponse submitReview(SubmitReviewRequest request);
}