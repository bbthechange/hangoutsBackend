package com.bbthechange.inviter.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Utility for tracking DynamoDB query performance.
 * Logs slow queries and records metrics for monitoring.
 */
@Component
public class QueryPerformanceTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceTracker.class);
    private static final long SLOW_QUERY_THRESHOLD_MS = 500L;
    
    private final MeterRegistry meterRegistry;
    
    @Autowired
    public QueryPerformanceTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Track a query operation with performance monitoring.
     * 
     * @param operation The operation name for logging/metrics
     * @param table The table name being queried
     * @param queryOperation The query operation to execute
     * @return The result of the query operation
     */
    public <T> T trackQuery(String operation, String table, Supplier<T> queryOperation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            T result = queryOperation.get();
            long duration = System.currentTimeMillis() - startTime;
            
            // Log slow queries for investigation
            if (duration > SLOW_QUERY_THRESHOLD_MS) {
                logger.warn("Slow DynamoDB query detected: operation={}, table={}, duration={}ms", 
                    operation, table, duration);
            } else {
                logger.debug("DynamoDB query completed: operation={}, table={}, duration={}ms", 
                    operation, table, duration);
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("DynamoDB query failed: operation={}, table={}, duration={}ms, error={}", 
                operation, table, duration, e.getMessage());
            throw e;
            
        } finally {
            // Record metrics for monitoring dashboards
            sample.stop(Timer.builder("dynamodb.query.duration")
                .tag("operation", operation)
                .tag("table", table)
                .register(meterRegistry));
        }
    }
}