package com.hivel.tracker.controller;

import com.hivel.tracker.dto.request.CreateGoalRequest;
import com.hivel.tracker.dto.request.UpdateGoalStatusRequest;
import com.hivel.tracker.dto.response.ApiResponse;
import com.hivel.tracker.dto.response.GoalResponse;
import com.hivel.tracker.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private static final Logger log = LoggerFactory.getLogger(GoalController.class);

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
        @RequestBody CreateGoalRequest request
    ) {
        log.info("Received request to create goal — employeeId: {}, cycleId: {}",
            request != null ? request.getEmployeeId() : null,
            request != null ? request.getCycleId() : null);

        GoalResponse response = goalService.createGoal(request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Goal created successfully",
                response
            ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoalStatus(
        @PathVariable("id") UUID goalId,
        @RequestBody UpdateGoalStatusRequest request
    ) {
        log.info("Received request to update goal status — goalId: {}, status: {}",
            goalId,
            request != null ? request.getStatus() : null);

        GoalResponse response = goalService.updateGoalStatus(goalId, request);

        return ResponseEntity.ok(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Goal status updated successfully",
                response
            )
        );
    }
}