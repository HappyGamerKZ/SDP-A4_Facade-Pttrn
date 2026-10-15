package org.aitu.se.facade;

import java.util.Map;

public interface IOrderQueue {
    String acceptNewOrder(Map<String, Integer> items);
    void compensateAndReturnItems(String orderId);
    void compensateWithoutReturningItems(String orderId);
}