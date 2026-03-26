package com.hivel.tracker.dto.request;

import com.hivel.tracker.enums.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGoalStatusRequest {

    private GoalStatus status;
}