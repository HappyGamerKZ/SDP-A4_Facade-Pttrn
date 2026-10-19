package org.aitu.se.checkout;

/** Notifies the courier service that a paid order can be delivered. */
public class DeliveryDispatcher {
    public void dispatch(Receipt receipt) {
        System.out.println("[Delivery] Receipt " + receipt.getReceiptId() + " is paid.");
        System.out.println("[Delivery] Courier assigned, the order is on its way to the customer.");
    }
}
