package org.aitu.se.backhouse;

public class QualityCheckFailedException extends RuntimeException {
    public QualityCheckFailedException(String message) {
        super(message);
    }
}
