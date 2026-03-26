package com.hivel.tracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFilterProjection {

    private UUID employeeId;
    private String name;
    private String department;
    private String role;
    private BigDecimal averageRating;
}