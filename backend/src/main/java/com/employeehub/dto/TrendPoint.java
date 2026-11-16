package com.employeehub.dto;

/** One month of the joining trend; month is formatted yyyy-MM. */
public record TrendPoint(String month, long count) { }
