package com.billing.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stores a List<BigDecimal> as a comma-separated string, e.g. "0,5,18,40".
 */
@Converter
public class DecimalListConverter implements AttributeConverter<List<BigDecimal>, String> {

    @Override
    public String convertToDatabaseColumn(List<BigDecimal> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(BigDecimal::toPlainString)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<BigDecimal> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BigDecimal::new)
                .collect(Collectors.toList());
    }
}
