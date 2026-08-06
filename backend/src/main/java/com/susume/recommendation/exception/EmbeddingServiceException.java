package com.susume.recommendation.exception;

public class EmbeddingServiceException extends RuntimeException {
    public EmbeddingServiceException(String message) {
        super(message);
    }

    public EmbeddingServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean isRetryable() {
        return getCause() instanceof java.net.SocketTimeoutException ||
                getCause() instanceof org.springframework.web.client.ResourceAccessException;
    }
}
