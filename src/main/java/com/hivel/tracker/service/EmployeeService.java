package com.hivel.tracker.service;

import com.hivel.tracker.dto.request.CreateEmployeeRequest;
import com.hivel.tracker.dto.response.EmployeeFilterResponse;
import com.hivel.tracker.dto.response.EmployeeResponse;
import com.hivel.tracker.dto.response.EmployeeReviewDetailsResponse;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {

    EmployeeResponse createEmployee(CreateEmployeeRequest request);

    List<EmployeeReviewDetailsResponse> getEmployeeReviews(UUID employeeId);

    List<EmployeeFilterResponse> filterEmployees(String department, Double minRating);
}