package org.aitu.se.facade;

public interface ICookingStationAllocator {
    String startCooking(String orderId, boolean forceFail);
    void compensateTicket(String ticketId);
}