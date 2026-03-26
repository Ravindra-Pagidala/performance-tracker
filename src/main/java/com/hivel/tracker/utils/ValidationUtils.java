package com.hivel.tracker.utils;

import com.hivel.tracker.exception.BusinessValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;


public final class ValidationUtils {

    private static final Logger log = LoggerFactory.getLogger(ValidationUtils.class);

    // Private constructor — this is a utility class, never instantiate it
    private ValidationUtils() {
        throw new UnsupportedOperationException(
            "ValidationUtils is a utility class and cannot be instantiated"
        );
    }

    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            log.warn("Validation failed — field '{}' is null or blank", fieldName);
            throw new BusinessValidationException(
                fieldName + " must not be null or blank"
            );
        }
    }

    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            log.warn("Validation failed — field '{}' is null", fieldName);
            throw new BusinessValidationException(
                fieldName + " must not be null"
            );
        }
    }

    public static void requireValidRating(Short rating) {
        if (rating == null) {
            log.warn("Validation failed — rating is null");
            throw new BusinessValidationException("Rating must not be null");
        }
        if (rating < 1 || rating > 5) {
            log.warn("Validation failed — rating {} is out of range [1-5]", rating);
            throw new BusinessValidationException(
                "Rating must be between 1 and 5, received: " + rating
            );
        }
    }


    public static void requireValidJoiningDate(LocalDate joiningDate) {
        if (joiningDate == null) {
            log.warn("Validation failed — joiningDate is null");
            throw new BusinessValidationException("Joining date must not be null");
        }
        if (joiningDate.isAfter(LocalDate.now().plusYears(1))) {
            log.warn("Validation failed — joiningDate {} is too far in future",
                joiningDate);
            throw new BusinessValidationException(
                "Joining date cannot be more than 1 year in the future: " + joiningDate
            );
        }
    }


    public static UUID requireValidUUID(String uuidString, String fieldName) {
        if (uuidString == null || uuidString.trim().isEmpty()) {
            log.warn("Validation failed — UUID field '{}' is null or blank", fieldName);
            throw new BusinessValidationException(
                fieldName + " must not be null or blank"
            );
        }
        try {
            return UUID.fromString(uuidString.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Validation failed — '{}' is not a valid UUID: {}",
                fieldName, uuidString);
            throw new BusinessValidationException(
                fieldName + " is not a valid UUID format: " + uuidString
            );
        }
    }


    public static void requireNotSelfReview(UUID employeeId, UUID reviewerId) {
        if (employeeId == null || reviewerId == null) {
            return; // null checks handled separately
        }
        if (employeeId.equals(reviewerId)) {
            log.warn("Validation failed — self-review attempt by employeeId: {}",
                employeeId);
            throw new BusinessValidationException(
                "An employee cannot review themselves. " +
                "employeeId and reviewerId must be different."
            );
        }
    }
}