package com.employeehub.dto;

import java.util.List;

public record ImportResult(int totalRows, int imported, int skipped, List<RowError> errors, boolean errorsTruncated) {
    public record RowError(int row, String message) { }
}
