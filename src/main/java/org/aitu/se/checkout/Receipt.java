package org.aitu.se.checkout;

public class Receipt {
    private final String receiptId;
    private final String ticketId; // Ссылка на кухонный тикет (Подсистема B)
    private final double amount;
    private CheckoutStatus status;

    public Receipt(String receiptId, String ticketId, double amount) {
        this.receiptId = receiptId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.status = CheckoutStatus.AWAITING_PAYMENT;
    }

    public Receipt(String receiptId, String ticketId, double amount, CheckoutStatus status) {
        this.receiptId = receiptId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.status = status;
    }

    public void transitionTo(CheckoutStatus newStatus) {
        if (this.status == CheckoutStatus.CANCELLED) {
            throw new IllegalStateException("Нельзя менять статус отмененного чека.");
        }

        if (this.status == CheckoutStatus.AWAITING_PAYMENT && newStatus == CheckoutStatus.PAID_AND_DISPATCHED) {
            this.status = newStatus;
        } else if (this.status == CheckoutStatus.AWAITING_PAYMENT && newStatus == CheckoutStatus.CANCELLED) {
            this.status = newStatus; // Сценарий ошибки
        } else {
            throw new IllegalStateException("Запрещенный переход чека: " + this.status + " -> " + newStatus);
        }
    }

    public String getReceiptId() { return receiptId; }
    public String getTicketId() { return ticketId; }
    public double getAmount() { return amount; }
    public CheckoutStatus getStatus() { return status; }
}