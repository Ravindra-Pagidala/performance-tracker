package com.hivel.tracker.controller;

import com.hivel.tracker.dto.request.CreateEmployeeRequest;
import com.hivel.tracker.dto.response.ApiResponse;
import com.hivel.tracker.dto.response.EmployeeFilterResponse;
import com.hivel.tracker.dto.response.EmployeeResponse;
import com.hivel.tracker.dto.response.EmployeeReviewDetailsResponse;
import com.hivel.tracker.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
        @RequestBody CreateEmployeeRequest request
    ) {
        log.info("Received request to create employee — name: {}, department: {}",
            request != null ? request.getName() : null,
            request != null ? request.getDepartment() : null);

        EmployeeResponse response = employeeService.createEmployee(request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                "Employee created successfully",
                response
            ));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<List<EmployeeReviewDetailsResponse>>> getEmployeeReviews(
        @PathVariable("id") UUID employeeId
    ) {
        log.info("Received request to fetch reviews for employeeId: {}", employeeId);

        List<EmployeeReviewDetailsResponse> response =
            employeeService.getEmployeeReviews(employeeId);

        return ResponseEntity.ok(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Employee reviews fetched successfully",
                response
            )
        );
    }

    @GetMapping(params = {"department", "minRating"})
    public ResponseEntity<ApiResponse<List<EmployeeFilterResponse>>> filterEmployees(
        @RequestParam("department") String department,
        @RequestParam("minRating") Double minRating
    ) {
        log.info("Received request to filter employees — department: {}, minRating: {}",
            department, minRating);

        List<EmployeeFilterResponse> response =
            employeeService.filterEmployees(department, minRating);

        return ResponseEntity.ok(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Filtered employees fetched successfully",
                response
            )
        );
    }
}