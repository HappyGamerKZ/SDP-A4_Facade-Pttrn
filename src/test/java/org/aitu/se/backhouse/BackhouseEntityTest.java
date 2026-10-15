package org.aitu.se.backhouse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackhouseEntityTest {

    @Test
    @DisplayName("Успешный жизненный цикл тикета кухни")
    void testKitchenTicketLifeCycle() {
        KitchenTicket ticket = new KitchenTicket("TCK-1", "ORD-1");
        assertEquals(KitchenStatus.PENDING_STATION, ticket.getStatus());
        assertEquals(-1, ticket.getStationId());

        ticket.setStationId(2);
        assertEquals(2, ticket.getStationId());

        ticket.transitionTo(KitchenStatus.PREPARING);
        assertEquals(KitchenStatus.PREPARING, ticket.getStatus());

        ticket.transitionTo(KitchenStatus.QUALITY_PASSED);
        assertEquals(KitchenStatus.QUALITY_PASSED, ticket.getStatus());
    }

    @Test
    @DisplayName("Откат тикета в состояние WASTED")
    void testKitchenTicketWasted() {
        KitchenTicket ticket = new KitchenTicket("TCK-2", "ORD-2", KitchenStatus.PREPARING, 1);
        ticket.transitionTo(KitchenStatus.WASTED);
        assertEquals(KitchenStatus.WASTED, ticket.getStatus());

        assertThrows(IllegalStateException.class, () -> ticket.transitionTo(KitchenStatus.QUALITY_PASSED));
    }

    @Test
    @DisplayName("Недопустимые переходы статуса тикета")
    void testInvalidTransitions() {
        KitchenTicket ticket = new KitchenTicket("TCK-3", "ORD-3");
        assertThrows(IllegalStateException.class, () -> ticket.transitionTo(KitchenStatus.QUALITY_PASSED));
    }

    @Test
    @DisplayName("Проверка исключений кухни")
    void testExceptions() {
        NoAvailableStationsException ex1 = new NoAvailableStationsException("Нет мест");
        assertEquals("Нет мест", ex1.getMessage());

        QualityCheckFailedException ex2 = new QualityCheckFailedException("Сгорело");
        assertEquals("Сгорело", ex2.getMessage());
    }
}