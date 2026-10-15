package org.aitu.se.facade;

public interface IBillingModule {
    String processPayment(String ticketId, double amount, boolean forceFail);
}