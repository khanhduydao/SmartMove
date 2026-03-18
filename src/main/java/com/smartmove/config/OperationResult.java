package com.smartmove.config;

/**
 * Result wrapper for operations that might fail.
 * Eliminates null returns and exception throwing for expected failures.
 */
public class OperationResult<T> {
    private final T value;
    private final boolean success;
    private final String errorMessage;
    
    private OperationResult(T value, boolean success, String errorMessage) {
        this.value = value;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(value, true, null);
    }
    
    public static <T> OperationResult<T> failure(String errorMessage) {
        return new OperationResult<>(null, false, errorMessage);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public T getValue() {
        if (!success) {
            throw new IllegalStateException("Cannot get value from failed operation: " + errorMessage);
        }
        return value;
    }
    
    public T getValueOrDefault(T defaultValue) {
        return success ? value : defaultValue;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public <U> OperationResult<U> map(java.util.function.Function<T, U> mapper) {
        if (success) {
            try {
                return success(mapper.apply(value));
            } catch (Exception e) {
                return failure("Mapping failed: " + e.getMessage());
            }
        }
        return failure(errorMessage);
    }
}
