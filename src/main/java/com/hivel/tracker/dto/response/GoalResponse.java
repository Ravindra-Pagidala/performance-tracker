package com.hivel.tracker.dto.response;

import com.hivel.tracker.enums.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalResponse {

    private UUID goalId;
    private UUID employeeId;
    private String employeeName;
    private UUID cycleId;
    private String cycleName;
    private String title;
    private GoalStatus status;
}