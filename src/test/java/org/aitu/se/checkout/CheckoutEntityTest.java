package org.aitu.se.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutEntityTest {

    @Test
    @DisplayName("Успешная обработка чека")
    void testReceiptSuccess() {
        Receipt receipt = new Receipt("RCP-1", "TCK-1", 5000.0);
        assertEquals(CheckoutStatus.AWAITING_PAYMENT, receipt.getStatus());
        assertEquals("RCP-1", receipt.getReceiptId());
        assertEquals("TCK-1", receipt.getTicketId());
        assertEquals(5000.0, receipt.getAmount());

        receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED);
        assertEquals(CheckoutStatus.PAID_AND_DISPATCHED, receipt.getStatus());
    }

    @Test
    @DisplayName("Отмена чека при сбое оплаты")
    void testReceiptCancellation() {
        Receipt receipt = new Receipt("RCP-2", "TCK-2", 3000.0, CheckoutStatus.AWAITING_PAYMENT);
        receipt.transitionTo(CheckoutStatus.CANCELLED);
        assertEquals(CheckoutStatus.CANCELLED, receipt.getStatus());

        assertThrows(IllegalStateException.class, () -> receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED));
    }

    @Test
    @DisplayName("Проверка PaymentFailedException")
    void testPaymentFailedException() {
        PaymentFailedException ex = new PaymentFailedException("Ошибка карты");
        assertEquals("Ошибка карты", ex.getMessage());
    }
}