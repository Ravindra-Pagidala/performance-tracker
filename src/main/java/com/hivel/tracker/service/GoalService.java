package com.hivel.tracker.service;

import com.hivel.tracker.dto.request.CreateGoalRequest;
import com.hivel.tracker.dto.request.UpdateGoalStatusRequest;
import com.hivel.tracker.dto.response.GoalResponse;

import java.util.UUID;

public interface GoalService {

    GoalResponse createGoal(CreateGoalRequest request);

    GoalResponse updateGoalStatus(UUID goalId, UpdateGoalStatusRequest request);
}