package com.smartmove.config;

import com.smartmove.config.LoggerFactory;
import java.util.logging.*;

/**
 * Centralized exception handling strategy.
 * Improves reliability through consistent error handling.
 */
public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    static {
        try {
            // Configure logging
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.ALL);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            logger.severe("Failed to configure logging: %s".formatted(e.getMessage()));
        }
    }
    
    /**
     * Handle validation exceptions.
     */
    public static void handleValidationError(String context, Exception e) {
        logger.log(Level.WARNING, "Validation error in " + context + ": " + e.getMessage(), e);
    }
    
    /**
     * Handle persistence exceptions with retry capability.
     */
    public static <T> T handlePersistenceOperation(String operation, 
                                                     PersistenceOperation<T> op,
                                                     int maxRetries) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                return op.execute();
            } catch (Exception e) {
                lastException = e;
                attempts++;
                logger.log(Level.WARNING, 
                    String.format("Persistence operation '%s' failed (attempt %d/%d): %s",
                        operation, attempts, maxRetries, e.getMessage()));
                
                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(100L * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        logger.log(Level.SEVERE, 
            "Persistence operation '" + operation + "' failed after " + maxRetries + " attempts",
            lastException);
        
        throw new PersistenceException(
            "Operation failed after " + maxRetries + " attempts: " + operation,
            lastException);
    }
    
    /**
     * Handle policy violations.
     */
    public static void handlePolicyViolation(String vehicleId, String policyName, Exception e) {
        logger.log(Level.INFO, 
            String.format("Policy violation for vehicle %s in %s: %s",
                vehicleId, policyName, e.getMessage()));
    }
    
    /**
     * Handle critical system errors.
     */
    public static void handleCriticalError(String context, Exception e) {
        logger.log(Level.SEVERE, "CRITICAL ERROR in " + context, e);
        // Could trigger alerts, notifications, etc.
    }
    
    /**
     * Log telemetry processing errors without crashing the monitor.
     */
    public static void handleTelemetryProcessingError(String vehicleId, Exception e) {
        logger.log(Level.WARNING,
            "Telemetry processing error for vehicle " + vehicleId + ": " + e.getMessage());
        // Continue processing other telemetry data
    }
    
    /**
     * Functional interface for persistence operations.
     */
    @FunctionalInterface
    public interface PersistenceOperation<T> {
        T execute() throws Exception;
    }
    
    /**
     * Custom exception for persistence failures.
     */
    public static class PersistenceException extends RuntimeException {
        public PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

