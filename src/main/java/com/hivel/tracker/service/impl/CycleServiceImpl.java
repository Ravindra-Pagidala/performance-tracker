package com.hivel.tracker.service.impl;

import com.hivel.tracker.dto.mapper.TrackerMapper;
import com.hivel.tracker.dto.response.CycleSummaryResponse;
import com.hivel.tracker.entity.EmployeeCycleStats;
import com.hivel.tracker.entity.ReviewCycle;
import com.hivel.tracker.enums.GoalStatus;
import com.hivel.tracker.exception.ResourceNotFoundException;
import com.hivel.tracker.repository.EmployeeCycleStatsRepository;
import com.hivel.tracker.repository.GoalRepository;
import com.hivel.tracker.repository.ReviewCycleRepository;
import com.hivel.tracker.service.CycleService;
import com.hivel.tracker.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleServiceImpl implements CycleService {

    private static final Logger log = LoggerFactory.getLogger(CycleServiceImpl.class);

    private final ReviewCycleRepository reviewCycleRepository;
    private final EmployeeCycleStatsRepository employeeCycleStatsRepository;
    private final GoalRepository goalRepository;

    @Override
    public CycleSummaryResponse getCycleSummary(UUID cycleId) {
        log.info("Fetching cycle summary for cycleId: {}", cycleId);

        ValidationUtils.requireNonNull(cycleId, "cycleId");

        ReviewCycle cycle = reviewCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review cycle not found with id: " + cycleId
                ));

        Double averageRating = cycle.getAverageRating();

        List<EmployeeCycleStats> topPerformers =
                employeeCycleStatsRepository.findTopPerformersByCycle(cycleId);

        EmployeeCycleStats topPerformer = topPerformers.isEmpty() ? null : topPerformers.get(0);

        Integer completedGoals = Math.toIntExact(
                goalRepository.countByCycleIdAndStatus(cycleId, GoalStatus.COMPLETED)
        );

        Integer missedGoals = Math.toIntExact(
                goalRepository.countByCycleIdAndStatus(cycleId, GoalStatus.MISSED)
        );

        return TrackerMapper.toCycleSummaryResponse(
                cycle,
                averageRating,
                topPerformer,
                completedGoals,
                missedGoals
        );
    }
}