package com.hivel.tracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFilterResponse {

    private UUID employeeId;
    private String employeeName;
    private String department;
    private String role;
    private Double averageRating;
}