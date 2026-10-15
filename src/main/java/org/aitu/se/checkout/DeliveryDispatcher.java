package org.aitu.se.checkout;

public class DeliveryDispatcher {
    public void dispatch(Receipt receipt) {
        System.out.println("[Delivery] Чек " + receipt.getReceiptId() + " оплачен.");
        System.out.println("[Delivery] Курьер назначен. Заказ отправлен клиенту!");
    }
}