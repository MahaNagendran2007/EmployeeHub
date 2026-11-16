package com.employeehub.exception;

import com.employeehub.dto.FieldErrorDetail;
import java.util.List;

public class DuplicateResourceException extends RuntimeException {

    private final transient List<FieldErrorDetail> fieldErrors;

    public DuplicateResourceException(List<FieldErrorDetail> fieldErrors) {
        super("An employee with the same unique value already exists");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
