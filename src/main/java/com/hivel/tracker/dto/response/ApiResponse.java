package com.hivel.tracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private OffsetDateTime timestamp;
    private Integer status;
    private String message;
    private T data;

    // Error-specific fields
    private String error;
    private String path;
    private List<String> details;

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return ApiResponse.<T>builder()
                .timestamp(OffsetDateTime.now())
                .status(status)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {
        return ApiResponse.<T>builder()
                .timestamp(OffsetDateTime.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}