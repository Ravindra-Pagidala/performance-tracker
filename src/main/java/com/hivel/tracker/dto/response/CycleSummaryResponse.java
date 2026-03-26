package com.hivel.tracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleSummaryResponse {

    private UUID cycleId;
    private String cycleName;
    private Double averageRating;

    private UUID topPerformerEmployeeId;
    private String topPerformerName;
    private Double topPerformerAverageRating;

    private Integer completedGoals;
    private Integer missedGoals;
}