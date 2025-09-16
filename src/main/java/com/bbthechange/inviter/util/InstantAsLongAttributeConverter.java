package com.bbthechange.inviter.util;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;

/**
 * Converts between Instant and DynamoDB Number (Long) attribute.
 * Stores Instants as epoch milliseconds.
 */
public class InstantAsLongAttributeConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        if (instant == null) {
            return AttributeValue.builder().nul(true).build();
        }
        return AttributeValue.builder().n(String.valueOf(instant.toEpochMilli())).build();
    }

    @Override
    public Instant transformTo(AttributeValue attributeValue) {
        if (attributeValue == null || Boolean.TRUE.equals(attributeValue.nul())) {
            return null;
        }
        
        if (attributeValue.n() != null) {
            return Instant.ofEpochMilli(Long.parseLong(attributeValue.n()));
        }
        
        // Handle case where it might be stored as a string (for backwards compatibility)
        if (attributeValue.s() != null) {
            try {
                // Try to parse as epoch millis first
                return Instant.ofEpochMilli(Long.parseLong(attributeValue.s()));
            } catch (NumberFormatException e) {
                // Fall back to ISO-8601 string parsing
                return Instant.parse(attributeValue.s());
            }
        }
        
        throw new IllegalArgumentException("Cannot convert attribute value to Instant: " + attributeValue);
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.N;
    }
}