package org.aitu.se.facade;

public enum ChainState {
    PENDING_B,        // Шаг А пройден, ждем Кухню
    PENDING_C,        // Шаг В пройден, ждем Оплату
    COMPLETED,        // Все шаги успешны
    COMPENSATED_AT_B, // Откат из-за ошибки на Кухне
    COMPENSATED_AT_C  // Откат из-за ошибки Оплаты
}
