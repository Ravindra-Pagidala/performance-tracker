package com.hivel.tracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID reviewId;
    private UUID employeeId;
    private String employeeName;
    private UUID cycleId;
    private String cycleName;
    private UUID reviewerId;
    private String reviewerName;
    private Short rating;
    private String notes;
    private OffsetDateTime submittedAt;
}