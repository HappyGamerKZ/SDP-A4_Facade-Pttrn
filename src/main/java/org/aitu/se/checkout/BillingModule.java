package org.aitu.se.checkout;

import org.aitu.se.facade.IBillingModule;

import java.util.UUID;

public class BillingModule implements IBillingModule {
    private final CheckoutRepository repository;
    private final DeliveryDispatcher deliveryDispatcher;

    public BillingModule(String checkout_db_url) {
        this.repository = new CheckoutRepository(checkout_db_url);
        this.deliveryDispatcher = new DeliveryDispatcher();
    }

    // НОРМАЛЬНЫЙ ПОТОК (Шаг C)
    @Override
    public String processPayment(String ticketId, double amount, boolean forcePaymentFail) {
        String receiptId = UUID.randomUUID().toString();
        Receipt receipt = new Receipt(receiptId, ticketId, amount);
        repository.save(receipt); // Сохраняем состояние "Ожидает оплаты"

        System.out.println("[Checkout] Попытка списать " + amount + " тг с карты клиента...");

        // Искусственная логика для демонстрации падения Шага C (Требование 3.4)
        if (forcePaymentFail) {
            receipt.transitionTo(CheckoutStatus.CANCELLED);
            repository.save(receipt);
            throw new PaymentFailedException("Недостаточно средств на карте или банк отклонил операцию!");
        }

        // Если оплата прошла успешно - вызываем доставку
        System.out.println("[Checkout] Оплата успешно прошла!");
        deliveryDispatcher.dispatch(receipt);

        // Финализируем
        receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED);
        repository.save(receipt);

        return receiptId;
    }
}