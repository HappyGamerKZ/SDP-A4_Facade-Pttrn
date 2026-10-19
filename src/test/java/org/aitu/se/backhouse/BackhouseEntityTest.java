package org.aitu.se.backhouse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for subsystem B: the {@link KitchenTicket} lifecycle and the pure {@link QualityChecker}
 * rule.
 */
class BackhouseEntityTest {

    @Test
    @DisplayName("KitchenTicket follows PENDING_STATION -> PREPARING -> QUALITY_PASSED")
    void validLifecycle() {
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
    @DisplayName("A ticket can be scrapped from any active state, but never revived")
    void wastedIsTerminal() {
        KitchenTicket preparing = new KitchenTicket("TCK-2", "ORD-2", KitchenStatus.PREPARING, 1);
        preparing.transitionTo(KitchenStatus.WASTED);
        assertEquals(KitchenStatus.WASTED, preparing.getStatus());
        assertThrows(IllegalStateException.class, () -> preparing.transitionTo(KitchenStatus.QUALITY_PASSED));

        KitchenTicket waiting = new KitchenTicket("TCK-3", "ORD-3");
        waiting.transitionTo(KitchenStatus.WASTED);
        assertEquals(KitchenStatus.WASTED, waiting.getStatus());
    }

    @Test
    @DisplayName("Skipping a step in the kitchen lifecycle is forbidden")
    void forbiddenTransition() {
        KitchenTicket ticket = new KitchenTicket("TCK-4", "ORD-4");
        assertThrows(IllegalStateException.class, () -> ticket.transitionTo(KitchenStatus.QUALITY_PASSED));
    }

    @Test
    @DisplayName("QualityChecker accepts a properly allocated ticket and rejects the rest")
    void qualityChecker() {
        QualityChecker checker = new QualityChecker();

        KitchenTicket good = new KitchenTicket("TCK-5", "ORD-5", KitchenStatus.PREPARING, 2);
        assertDoesNotThrow(() -> checker.verifyMeal(good));

        KitchenTicket notCooking = new KitchenTicket("TCK-6", "ORD-6");
        assertThrows(QualityCheckFailedException.class, () -> checker.verifyMeal(notCooking));

        KitchenTicket noStation = new KitchenTicket("TCK-7", "ORD-7", KitchenStatus.PREPARING, -1);
        assertThrows(QualityCheckFailedException.class, () -> checker.verifyMeal(noStation));

        assertThrows(QualityCheckFailedException.class, () -> checker.verifyMeal(null));
    }

    @Test
    @DisplayName("Kitchen exceptions carry their messages")
    void exceptionMessages() {
        assertEquals("no station", new NoAvailableStationsException("no station").getMessage());
        assertEquals("bad quality", new QualityCheckFailedException("bad quality").getMessage());
        assertEquals("on stop-list", new DishUnavailableException("on stop-list").getMessage());
    }
}
