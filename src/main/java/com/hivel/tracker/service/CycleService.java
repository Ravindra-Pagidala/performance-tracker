package com.hivel.tracker.service;

import com.hivel.tracker.dto.response.CycleSummaryResponse;

import java.util.UUID;

public interface CycleService {

    CycleSummaryResponse getCycleSummary(UUID cycleId);
}