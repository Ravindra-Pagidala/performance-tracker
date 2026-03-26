package com.hivel.tracker.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReviewRequest {

    private String employeeId;
    private String cycleId;
    private String reviewerId;
    private Integer rating;
    private String notes;
}