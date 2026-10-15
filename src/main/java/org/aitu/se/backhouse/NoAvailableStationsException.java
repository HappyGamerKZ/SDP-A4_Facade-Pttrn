package org.aitu.se.backhouse;

public class NoAvailableStationsException extends RuntimeException {
    public NoAvailableStationsException(String message) {
        super(message);
    }
}
