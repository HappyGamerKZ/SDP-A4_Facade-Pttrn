package org.aitu.se.facade;

// Заказ отклонен на первом шаге (A): ничего не закоммичено, компенсировать нечего
public class OrderRejectedException extends RuntimeException {
    public OrderRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
