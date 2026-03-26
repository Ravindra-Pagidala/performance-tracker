package com.hivel.tracker.service.impl;

import com.hivel.tracker.dto.mapper.TrackerMapper;
import com.hivel.tracker.dto.request.CreateGoalRequest;
import com.hivel.tracker.dto.request.UpdateGoalStatusRequest;
import com.hivel.tracker.dto.response.GoalResponse;
import com.hivel.tracker.entity.Employee;
import com.hivel.tracker.entity.Goal;
import com.hivel.tracker.entity.ReviewCycle;
import com.hivel.tracker.enums.GoalStatus;
import com.hivel.tracker.exception.BusinessValidationException;
import com.hivel.tracker.exception.ResourceNotFoundException;
import com.hivel.tracker.repository.EmployeeRepository;
import com.hivel.tracker.repository.GoalRepository;
import com.hivel.tracker.repository.ReviewCycleRepository;
import com.hivel.tracker.service.GoalService;
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
public class GoalServiceImpl implements GoalService {

    private static final Logger log = LoggerFactory.getLogger(GoalServiceImpl.class);

    private final GoalRepository goalRepository;
    private final EmployeeRepository employeeRepository;
    private final ReviewCycleRepository reviewCycleRepository;

    @Override
    @Transactional
    public GoalResponse createGoal(CreateGoalRequest request) {
        log.info("Creating goal — employeeId: {}, cycleId: {}, title: {}",
            request.getEmployeeId(), request.getCycleId(), request.getTitle());

        ValidationUtils.requireNonNull(request, "CreateGoalRequest");
        ValidationUtils.requireNonNull(request.getEmployeeId(), "employeeId");
        ValidationUtils.requireNonNull(request.getCycleId(), "cycleId");
        ValidationUtils.requireNonBlank(request.getTitle(), "title");

        Employee employee = employeeRepository.findByIdAndIsActiveTrue(request.getEmployeeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Employee not found with id: " + request.getEmployeeId()
            ));

        ReviewCycle cycle = reviewCycleRepository.findById(request.getCycleId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Review cycle not found with id: " + request.getCycleId()
            ));

        Goal goal = Goal.builder()
            .employee(employee)
            .cycle(cycle)
            .title(request.getTitle().trim())
            .status(GoalStatus.PENDING)
            .build();

        Goal savedGoal = goalRepository.save(goal);

        log.info("Goal created successfully — goalId: {}", savedGoal.getId());

        return TrackerMapper.toGoalResponse(savedGoal);
    }

    @Override
    @Transactional
    public GoalResponse updateGoalStatus(UUID goalId, UpdateGoalStatusRequest request) {
        log.info("Updating goal status — goalId: {}, newStatus: {}",
            goalId, request != null ? request.getStatus() : null);

        ValidationUtils.requireNonNull(goalId, "goalId");
        ValidationUtils.requireNonNull(request, "UpdateGoalStatusRequest");
        ValidationUtils.requireNonNull(request.getStatus(), "status");

        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Goal not found with id: " + goalId
            ));

        GoalStatus newStatus = request.getStatus();

        if (goal.getStatus() == newStatus) {
            throw new BusinessValidationException(
                "Goal already has status: " + newStatus
            );
        }

        goal.setStatus(newStatus);

        Goal updatedGoal = goalRepository.save(goal);

        log.info("Goal status updated successfully — goalId: {}, status: {}",
            updatedGoal.getId(), updatedGoal.getStatus());

        return TrackerMapper.toGoalResponse(updatedGoal);
    }
}