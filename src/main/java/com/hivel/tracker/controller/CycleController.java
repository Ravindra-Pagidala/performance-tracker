package com.hivel.tracker.controller;

import com.hivel.tracker.dto.response.ApiResponse;
import com.hivel.tracker.dto.response.CycleSummaryResponse;
import com.hivel.tracker.service.CycleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cycles")
@RequiredArgsConstructor
public class CycleController {

    private static final Logger log = LoggerFactory.getLogger(CycleController.class);

    private final CycleService cycleService;

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<CycleSummaryResponse>> getCycleSummary(
        @PathVariable("id") UUID cycleId
    ) {
        log.info("Received request to fetch cycle summary for cycleId: {}", cycleId);

        CycleSummaryResponse response = cycleService.getCycleSummary(cycleId);

        return ResponseEntity.ok(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Cycle summary fetched successfully",
                response
            )
        );
    }
}