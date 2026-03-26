package com.hivel.tracker.service.impl;

import com.hivel.tracker.dto.mapper.TrackerMapper;
import com.hivel.tracker.dto.request.CreateEmployeeRequest;
import com.hivel.tracker.dto.response.EmployeeFilterResponse;
import com.hivel.tracker.dto.response.EmployeeResponse;
import com.hivel.tracker.dto.response.EmployeeReviewDetailsResponse;
import com.hivel.tracker.entity.Employee;
import com.hivel.tracker.entity.PerformanceReview;
import com.hivel.tracker.exception.BusinessValidationException;
import com.hivel.tracker.exception.ResourceNotFoundException;
import com.hivel.tracker.repository.EmployeeRepository;
import com.hivel.tracker.repository.PerformanceReviewRepository;
import com.hivel.tracker.service.EmployeeService;
import com.hivel.tracker.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final EmployeeRepository employeeRepository;
    private final PerformanceReviewRepository performanceReviewRepository;

    @Override
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        log.info("Creating employee — name: {}, department: {}, role: {}",
            request.getName(), request.getDepartment(), request.getRole());

        ValidationUtils.requireNonNull(request, "CreateEmployeeRequest");
        ValidationUtils.requireNonBlank(request.getName(), "name");
        ValidationUtils.requireNonBlank(request.getDepartment(), "department");
        ValidationUtils.requireNonBlank(request.getRole(), "role");
        ValidationUtils.requireValidJoiningDate(request.getJoiningDate());

        Employee employee = Employee.builder()
            .name(request.getName().trim())
            .department(request.getDepartment().trim())
            .role(request.getRole().trim())
            .joiningDate(request.getJoiningDate())
            .isActive(true)
            .build();

        Employee saved = employeeRepository.save(employee);

        log.info("Employee created successfully — id: {}", saved.getId());

        return TrackerMapper.toEmployeeResponse(saved);
    }

    @Override
    public List<EmployeeReviewDetailsResponse> getEmployeeReviews(UUID employeeId) {
        log.info("Fetching reviews for employeeId: {}", employeeId);

        ValidationUtils.requireNonNull(employeeId, "employeeId");

        Employee employee = employeeRepository.findByIdAndIsActiveTrue(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Employee not found with id: " + employeeId
            ));

        List<PerformanceReview> reviews =
            performanceReviewRepository.findEmployeeReviewsWithCycleAndReviewer(employee.getId());

        return reviews.stream()
            .map(TrackerMapper::toEmployeeReviewDetailsResponse)
            .toList();
    }

    @Override
    public List<EmployeeFilterResponse> filterEmployees(String department, Double minRating) {
        log.info("Filtering employees — department: {}, minRating: {}", department, minRating);

        ValidationUtils.requireNonBlank(department, "department");

        if (minRating == null) {
            throw new BusinessValidationException("minRating must not be null");
        }

        if (minRating < 0 || minRating > 5) {
            throw new BusinessValidationException(
                "minRating must be between 0 and 5, received: " + minRating
            );
        }

        List<Employee> employees = employeeRepository.findEmployeesByDepartmentAndMinRating(
            department.trim(),
            BigDecimal.valueOf(minRating)
        );

        return employees.stream()
            .map(employee -> {
                Double avg = performanceReviewRepository.findAverageRatingForEmployee(employee.getId());
                return TrackerMapper.toEmployeeFilterResponse(
                    employee,
                    avg != null ? round(avg) : 0.0
                );
            })
            .sorted(Comparator.comparing(EmployeeFilterResponse::getAverageRating).reversed())
            .toList();
    }

    private double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}