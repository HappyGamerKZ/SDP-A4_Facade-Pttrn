package org.aitu.se.backhouse;

/** Thrown when a dish cannot be cooked because it is currently on the kitchen stop-list. */
public class DishUnavailableException extends RuntimeException {
    public DishUnavailableException(String message) {
        super(message);
    }
}
